package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.CouponDTO;
import com.ecommerce.entity.Coupon;
import com.ecommerce.entity.UserCoupon;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.mapper.UserCouponMapper;
import com.ecommerce.entity.Shop;
import com.ecommerce.service.CouponService;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.UserCouponVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final ShopService shopService;

    @Override
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> list = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .le(Coupon::getStartAt, now)
                .and(w -> w.isNull(Coupon::getEndAt).or().ge(Coupon::getEndAt, now))
                .apply("claimed < total")
                // 关闭店铺的店铺券不展示：与 claim() 的「店铺暂停营业」校验保持一致。
                // 平台券 shop_id 为 NULL，必须显式保留，否则会被 IN 子查询过滤掉
                .and(w -> w.isNull(Coupon::getShopId)
                        .or()
                        .inSql(Coupon::getShopId, "SELECT id FROM shop WHERE status = 1 AND deleted = 0"))
                .orderByDesc(Coupon::getCreatedAt));
        fillShopNames(list);
        return list;
    }

    @Override
    public List<UserCouponVO> listMine(Long userId) {
        List<Map<String, Object>> rows = userCouponMapper.selectMaps(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getCreatedAt));
        List<UserCouponVO> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long couponId = toLong(r.get("coupon_id"));
            Coupon c = couponMapper.selectById(couponId);
            if (c == null) continue;
            result.add(toVO(c, r));
        }
        return result;
    }

    @Override
    public List<UserCouponVO> listUsable(Long userId, BigDecimal amount) {
        List<UserCoupon> list = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, "UNUSED")
                .orderByDesc(UserCoupon::getCreatedAt));
        List<UserCouponVO> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserCoupon uc : list) {
            Coupon c = couponMapper.selectById(uc.getCouponId());
            if (c == null || c.getStatus() == null || c.getStatus() != 1) continue;
            if (c.getStartAt() != null && now.isBefore(c.getStartAt())) continue;
            if (c.getEndAt() != null && now.isAfter(c.getEndAt())) continue;
            // 传入结算金额时按门槛过滤，别让「列表显示可用、结算时被拒」
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                    && c.getThreshold() != null && c.getThreshold().compareTo(amount) > 0) continue;
            result.add(toVO(c, uc));
        }
        return result;
    }

    @Override
    @Transactional
    public void claim(Long userId, Long couponId) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BusinessException("优惠券不存在或已下架");
        }
        // 店铺券：店铺关店期间不让领取，别让用户领了却无法使用
        if (c.getShopId() != null) {
            Shop shop = shopService.detail(c.getShopId());
            if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
                throw new BusinessException("该店铺暂停营业，暂无可用场景");
            }
        }
        // 原子递增 claimed，领完即止（别让超发）
        int rows = couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .setSql("claimed = claimed + 1")
                .eq(Coupon::getId, couponId)
                .lt(Coupon::getClaimed, c.getTotal()));
        if (rows == 0) {
            throw new BusinessException("手慢了，优惠券已被领完");
        }
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus("UNUSED");
        try {
            userCouponMapper.insert(uc); // uk_user_coupon 防止重复领取
        } catch (DuplicateKeyException e) {
            // 已领取过，回滚 claimed 计数
            couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                    .setSql("claimed = claimed - 1")
                    .eq(Coupon::getId, couponId));
            throw new BusinessException("您已领取过该优惠券");
        }
    }

    @Override
    public IPage<Coupon> adminPage(int current, int size, String keyword, Long scopeShopId) {
        LambdaQueryWrapper<Coupon> q = new LambdaQueryWrapper<>();
        if (scopeShopId != null) q.eq(Coupon::getShopId, scopeShopId);
        if (keyword != null && !keyword.trim().isEmpty()) {
            q.like(Coupon::getName, keyword.trim());
        }
        q.orderByDesc(Coupon::getCreatedAt);
        IPage<Coupon> page = couponMapper.selectPage(new Page<>(current, size), q);
        fillShopNames(page.getRecords());
        return page;
    }

    @Override
    @Transactional
    public void save(CouponDTO dto, Long scopeShopId) {
        Coupon c = new Coupon();
        applyDto(c, dto);
        validate(c);
        c.setShopId(resolveShopId(dto.getShopId(), scopeShopId));
        c.setClaimed(0);
        couponMapper.insert(c);
        log.info("新建优惠券 {}（适用店铺：{}）", c.getName(), c.getShopId() == null ? "全平台" : c.getShopId());
    }

    @Override
    @Transactional
    public void update(Long id, CouponDTO dto, Long scopeShopId) {
        Coupon c = couponMapper.selectById(id);
        if (c == null) throw new BusinessException("优惠券不存在");
        checkOwner(c, scopeShopId);
        applyDto(c, dto);
        validate(c);
        // 适用范围创建后固定：已领取的券若突然从店铺券变全平台券，会破坏既有订单的抵扣规则
        couponMapper.updateById(c);
    }

    @Override
    @Transactional
    public void delete(Long id, Long scopeShopId) {
        Coupon c = couponMapper.selectById(id);
        if (c == null) throw new BusinessException("优惠券不存在");
        checkOwner(c, scopeShopId);
        couponMapper.deleteById(id);
    }

    /** 商家只能维护本店券；平台管理员（scopeShopId = null）可维护任意券 */
    private void checkOwner(Coupon c, Long scopeShopId) {
        if (scopeShopId == null) return;
        if (!scopeShopId.equals(c.getShopId())) {
            throw new BusinessException(403, "只能维护本店铺的优惠券");
        }
    }

    /** 计算落库的适用店铺：商家强制本店；管理员可指定店铺或留空表示全平台 */
    private Long resolveShopId(Long dtoShopId, Long scopeShopId) {
        if (scopeShopId != null) return scopeShopId;
        if (dtoShopId == null) return null;
        Shop shop = shopService.detail(dtoShopId);
        if (shop == null) throw new BusinessException("店铺不存在");
        return shop.getId();
    }

    /**
     * 券面额校验：防止 0 元核销（discount ≥ 门槛）或负数抵扣把订单金额反向抬高。
     * 前端只有提示，后端必须兜底。
     */
    private void validate(Coupon c) {
        if (c.getThreshold() == null || c.getThreshold().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("使用门槛不能为负数");
        }
        if (c.getDiscount() == null || c.getDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("减免金额必须大于 0");
        }
        if (c.getThreshold().compareTo(BigDecimal.ZERO) > 0
                && c.getDiscount().compareTo(c.getThreshold()) >= 0) {
            throw new BusinessException("减免金额必须小于使用门槛");
        }
        if (c.getTotal() == null || c.getTotal() < 0) {
            throw new BusinessException("发行总量不能为负数");
        }
        if (c.getStartAt() != null && c.getEndAt() != null && !c.getEndAt().isAfter(c.getStartAt())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
    }

    /** 批量回填店铺名，前端据此展示「全平台 / 某店铺」 */
    private void fillShopNames(List<Coupon> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(Coupon::getShopId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        Map<Long, String> names = shopService.nameMap(ids);
        list.forEach(c -> c.setShopName(c.getShopId() == null ? null : names.get(c.getShopId())));
    }

    private void applyDto(Coupon c, CouponDTO dto) {
        if (dto.getName() != null) c.setName(dto.getName());
        if (dto.getDescription() != null) c.setDescription(dto.getDescription());
        if (dto.getThreshold() != null) c.setThreshold(dto.getThreshold().setScale(2, RoundingMode.HALF_UP));
        if (dto.getDiscount() != null) c.setDiscount(dto.getDiscount().setScale(2, RoundingMode.HALF_UP));
        if (dto.getTotal() != null) {
            int claimed = c.getClaimed() == null ? 0 : c.getClaimed();
            if (dto.getTotal() < claimed) {
                throw new BusinessException("发行总量不能小于已领取数量（" + claimed + " 张）");
            }
            c.setTotal(dto.getTotal());
        }
        if (dto.getStartAt() != null) c.setStartAt(dto.getStartAt());
        // 结束时间允许置空表示「长期有效」：不能像其它字段那样只在非空时赋值，否则永远改不回长期
        c.setEndAt(dto.getEndAt());
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
    }

    // ---- VO 转换 ----

    private UserCouponVO toVO(Coupon c, Map<String, Object> row) {
        UserCouponVO v = new UserCouponVO();
        v.setId(toLong(row.get("id")));
        v.setCouponId(c.getId());
        v.setName(c.getName());
        v.setDescription(c.getDescription());
        v.setThreshold(c.getThreshold());
        v.setDiscount(c.getDiscount());
        v.setShopId(c.getShopId());
        v.setShopName(shopName(c));
        v.setStatus((String) row.get("status"));
        v.setStartAt(c.getStartAt());
        v.setEndAt(c.getEndAt());
        v.setUsedAt(toLdt(row.get("usedAt")));
        v.setClaimTime(toLdt(row.get("createdAt")));
        return v;
    }

    private UserCouponVO toVO(Coupon c, UserCoupon uc) {
        UserCouponVO v = new UserCouponVO();
        v.setId(uc.getId());
        v.setCouponId(c.getId());
        v.setName(c.getName());
        v.setDescription(c.getDescription());
        v.setThreshold(c.getThreshold());
        v.setDiscount(c.getDiscount());
        v.setShopId(c.getShopId());
        v.setShopName(shopName(c));
        v.setStatus(uc.getStatus());
        v.setStartAt(c.getStartAt());
        v.setEndAt(c.getEndAt());
        v.setUsedAt(uc.getUsedAt());
        v.setClaimTime(uc.getCreatedAt());
        return v;
    }

    /** 店铺券返回店铺名；全平台券返回 null，前端显示「全平台」 */
    private String shopName(Coupon c) {
        if (c.getShopId() == null) return null;
        return shopService.nameMap(List.of(c.getShopId())).get(c.getShopId());
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(o));
    }

    private LocalDateTime toLdt(Object o) {
        if (o instanceof LocalDateTime t) return t;
        return null;
    }
}

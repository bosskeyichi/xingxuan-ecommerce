package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.dto.ShopDTO;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Orders;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Shop;
import com.ecommerce.entity.User;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.ShopMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserMapper userMapper;

    @Override
    public List<Shop> listActive() {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, 1)
                .orderByAsc(Shop::getId));
    }

    @Override
    public List<Shop> search(String keyword, int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        LambdaQueryWrapper<Shop> w = new LambdaQueryWrapper<Shop>().eq(Shop::getStatus, 1);
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            w.and(x -> x.like(Shop::getName, kw).or().like(Shop::getIntro, kw));
        }
        w.orderByAsc(Shop::getId).last("LIMIT " + size);
        return shopMapper.selectList(w);
    }

    @Override
    public Shop detail(Long id) {
        Shop s = shopMapper.selectById(id);
        if (s == null) throw new BusinessException("店铺不存在");
        return s;
    }

    @Override
    public Shop mine() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) return null;
        return shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getOwnerId, uid));
    }

    @Override
    public Shop getByOwner(Long ownerId) {
        if (ownerId == null) return null;
        return shopMapper.selectOne(new LambdaQueryWrapper<Shop>().eq(Shop::getOwnerId, ownerId));
    }

    @Override
    public Long currentShopId() {
        // 平台管理员 = 不过滤；其他角色必须绑定店铺，别让「无店商家」被当成全平台视角
        if (ROLE_ADMIN.equals(LoginInterceptor.ROLE.get())) return null;
        Shop s = mine();
        if (s == null) {
            throw new BusinessException(403, "当前账号还没有绑定店铺，请先在「店铺管理」完善店铺信息");
        }
        return s.getId();
    }

    @Override
    public Long requireShopId() {
        Long shopId = currentShopId();
        if (shopId == null) {
            throw new BusinessException(403, "当前账号还没有绑定店铺，无法执行该操作");
        }
        return shopId;
    }

    @Override
    public Long requireOperableShopId() {
        // 只认自己名下的店铺：商家=本店；超管也只能经营自己店铺（如官方旗舰店）的订单
        Shop s = mine();
        if (s == null) {
            throw new BusinessException(403, "当前账号还没有绑定店铺，无法执行该操作");
        }
        return s.getId();
    }

    @Override
    public List<ShopVO> adminList() {
        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>().orderByAsc(Shop::getId));
        if (shops.isEmpty()) return List.of();

        Map<Long, Long> productCounts = productMapper.selectList(null).stream()
                .filter(p -> p.getShopId() != null)
                .collect(Collectors.groupingBy(Product::getShopId, Collectors.counting()));

        List<Orders> orders = orderMapper.selectList(null);
        Map<Long, Long> orderCounts = orders.stream()
                .filter(o -> o.getShopId() != null)
                .collect(Collectors.groupingBy(Orders::getShopId, Collectors.counting()));

        Map<Long, BigDecimal> salesMap = new HashMap<>();
        for (Orders o : orders) {
            if (o.getShopId() == null) continue;
            if (!List.of("PAID", "SHIPPED", "COMPLETED").contains(o.getStatus())) continue;
            BigDecimal amount = (o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                    .subtract(o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount());
            salesMap.merge(o.getShopId(), amount, BigDecimal::add);
        }

        Map<Long, User> owners = new HashMap<>();
        Set<Long> ownerIds = shops.stream().map(Shop::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!ownerIds.isEmpty()) {
            owners = userMapper.selectBatchIds(ownerIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }

        List<ShopVO> result = new ArrayList<>();
        for (Shop s : shops) {
            ShopVO v = new ShopVO();
            v.setId(s.getId());
            v.setOwnerId(s.getOwnerId());
            User owner = owners.get(s.getOwnerId());
            v.setOwnerName(owner == null ? null : (owner.getNickname() == null ? owner.getUsername() : owner.getNickname()));
            v.setOwnerUsername(owner == null ? null : owner.getUsername());
            v.setName(s.getName());
            v.setIntro(s.getIntro());
            v.setStatus(s.getStatus());
            v.setProductCount(productCounts.getOrDefault(s.getId(), 0L));
            v.setOrderCount(orderCounts.getOrDefault(s.getId(), 0L));
            v.setSalesAmount(salesMap.getOrDefault(s.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            result.add(v);
        }
        return result;
    }

    @Override
    @Transactional
    public void updateMine(ShopDTO dto) {
        // 只改自己名下的店铺资料：商家=本店，超管=自己开的店（官方旗舰店）
        Long shopId = requireOperableShopId();
        Shop s = shopMapper.selectById(shopId);
        if (s == null) throw new BusinessException("店铺不存在");
        s.setName(dto.getName());
        s.setIntro(dto.getIntro());
        if (dto.getCover() != null) s.setCover(dto.getCover());
        shopMapper.updateById(s);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法");
        }
        Shop s = shopMapper.selectById(id);
        if (s == null) throw new BusinessException("店铺不存在");
        Shop upd = new Shop();
        upd.setId(id);
        upd.setStatus(status);
        shopMapper.updateById(upd);
    }

    @Override
    public Map<Long, String> nameMap(Collection<Long> shopIds) {
        // 注意：不能返回 Map.of()，不可变 Map 的 get(null) 会抛 NPE，
        // 而订单的 shop_id 允许为空（历史数据），调用点会拿 null 来查店铺名。
        Map<Long, String> map = new HashMap<>();
        if (shopIds == null || shopIds.isEmpty()) return map;
        Set<Long> ids = shopIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return map;
        for (Shop s : shopMapper.selectBatchIds(ids)) {
            map.put(s.getId(), s.getName());
        }
        return map;
    }
}

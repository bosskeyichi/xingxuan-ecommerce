package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.ReviewDTO;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.mapper.*;
import com.ecommerce.service.ReviewService;
import com.ecommerce.vo.ReviewAppendVO;
import com.ecommerce.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewAppendMapper reviewAppendMapper;
    private final ReviewHiddenMapper reviewHiddenMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserSettingMapper settingMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ReviewVO> listByProduct(Long productId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .orderByDesc(Review::getCreatedAt));
        if (reviews.isEmpty()) return List.of();
        Set<Long> hidden = hiddenIds();
        reviews.removeIf(r -> hidden.contains(r.getId()));
        // 用户在隐私设置里关闭「公开我的评价」后，其评价不再出现在商品页
        filterPrivate(reviews);
        return toVOs(reviews, hidden, true);
    }

    @Override
    public List<ReviewVO> listMineByOrder(Long userId, String orderNo) {
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId));
        if (order == null) throw new BusinessException("订单不存在或无权操作");

        List<Long> productIds = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()))
                .stream().map(OrderItem::getProductId).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) return List.of();

        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .in(Review::getProductId, productIds)
                .orderByDesc(Review::getCreatedAt));
        // 自己的评价不做匿名遮罩
        return toVOs(reviews, hiddenIds(), false);
    }

    @Override
    @Transactional
    public void add(Long userId, ReviewDTO dto) {
        Product p = productMapper.selectById(dto.getProductId());
        if (p == null) throw new BusinessException("商品不存在");

        Long orderId = null;
        if (StringUtils.hasText(dto.getOrderNo())) {
            Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                    .eq(Orders::getOrderNo, dto.getOrderNo())
                    .eq(Orders::getUserId, userId));
            if (order == null) throw new BusinessException("订单不存在或无权操作");
            if (!"COMPLETED".equals(order.getStatus())) {
                throw new BusinessException("请确认收货后再评价");
            }
            Long matched = orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getOrderId, order.getId())
                    .eq(OrderItem::getProductId, dto.getProductId()));
            if (matched == null || matched == 0) {
                throw new BusinessException("该订单不包含此商品");
            }
            orderId = order.getId();
        } else {
            // 商品详情页直接评价：与订单页入口统一为「确认收货后」，
            // 否则同一用户刚付款就能绕过订单页的评价门槛
            LambdaQueryWrapper<Orders> ow = new LambdaQueryWrapper<Orders>()
                    .eq(Orders::getUserId, userId)
                    .eq(Orders::getStatus, "COMPLETED")
                    .select(Orders::getId);
            Long bought = orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                    .eq(OrderItem::getProductId, dto.getProductId())
                    .in(OrderItem::getOrderId, ow));
            if (bought == null || bought == 0) {
                throw new BusinessException("确认收货后才能评价该商品");
            }
        }

        Long exist = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getProductId, dto.getProductId()));
        if (exist != null && exist > 0) {
            throw new BusinessException("您已评价过该商品，可在「我的订单」中修改或追加评价");
        }

        Review r = new Review();
        r.setUserId(userId);
        r.setProductId(dto.getProductId());
        r.setOrderId(orderId);
        r.setRating(dto.getRating());
        r.setContent(dto.getContent());
        r.setAnonymous(flag(dto.getAnonymous()));
        r.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(r);
    }

    @Override
    @Transactional
    public void update(Long userId, Long reviewId, ReviewDTO dto) {
        Review r = ownReview(userId, reviewId);
        r.setRating(dto.getRating());
        r.setContent(dto.getContent());
        r.setAnonymous(flag(dto.getAnonymous()));
        reviewMapper.updateById(r);
    }

    @Override
    @Transactional
    public void append(Long userId, Long reviewId, String content) {
        if (!StringUtils.hasText(content)) throw new BusinessException("追加内容不能为空");
        if (content.trim().length() > 500) throw new BusinessException("追加内容不能超过 500 个字符");
        ownReview(userId, reviewId);

        ReviewAppend a = new ReviewAppend();
        a.setReviewId(reviewId);
        a.setContent(content.trim());
        a.setCreatedAt(LocalDateTime.now());
        reviewAppendMapper.insert(a);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException("评价不存在");
        String role = LoginInterceptor.ROLE.get();
        if (!Objects.equals(r.getUserId(), userId) && !"ADMIN".equals(role)) {
            throw new BusinessException("无权删除他人评价");
        }
        cleanUpAndDelete(reviewId);
    }

    @Override
    public Map<String, Object> ratingSummary(Long productId) {
        List<Review> reviews = reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId));
        Set<Long> hidden = hiddenIds();
        reviews.removeIf(r -> hidden.contains(r.getId()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", reviews.size());
        if (reviews.isEmpty()) {
            data.put("avgRating", BigDecimal.ZERO);
        } else {
            double avg = reviews.stream()
                    .mapToInt(r -> r.getRating() == null ? 0 : r.getRating())
                    .average().orElse(0d);
            data.put("avgRating", BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
        }
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            final int star = i;
            distribution.put(i, reviews.stream().filter(r -> star == (r.getRating() == null ? 0 : r.getRating())).count());
        }
        data.put("distribution", distribution);
        return data;
    }

    @Override
    public IPage<ReviewVO> adminPage(Long productId, long current, long size) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Review>()
                        .eq(productId != null, Review::getProductId, productId)
                        .orderByDesc(Review::getCreatedAt));
        // 后台治理需要看到真实账号，不做匿名遮罩
        List<ReviewVO> vos = toVOs(page.getRecords(), hiddenIds(), false);
        Page<ReviewVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public IPage<ReviewVO> minePage(Long userId, long current, long size) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getId));
        List<ReviewVO> vos = toVOs(page.getRecords(), hiddenIds(), false);
        return new Page<ReviewVO>(page.getCurrent(), page.getSize(), page.getTotal()).setRecords(vos);
    }

    @Override
    @Transactional
    public void batchDelete(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要删除的评价");
        if (ids.size() > 50) throw new BusinessException("一次最多删除 50 条评价");
        List<Review> list = reviewMapper.selectBatchIds(ids);
        for (Review r : list) {
            if (!Objects.equals(r.getUserId(), userId)) {
                throw new BusinessException(403, "只能删除自己的评价");
            }
        }
        for (Review r : list) {
            cleanUpAndDelete(r.getId());
        }
    }

    @Override
    @Transactional
    public void adminDelete(Long reviewId) {
        if (reviewMapper.selectById(reviewId) == null) throw new BusinessException("评价不存在");
        cleanUpAndDelete(reviewId);
    }

    @Override
    @Transactional
    public void setHidden(Long reviewId, boolean hidden) {
        if (reviewMapper.selectById(reviewId) == null) throw new BusinessException("评价不存在");
        if (hidden) {
            if (reviewHiddenMapper.selectById(reviewId) == null) {
                ReviewHidden h = new ReviewHidden();
                h.setReviewId(reviewId);
                h.setCreatedAt(LocalDateTime.now());
                reviewHiddenMapper.insert(h);
            }
        } else {
            reviewHiddenMapper.deleteById(reviewId);
        }
    }

    // ---- helpers ----

    private Review ownReview(Long userId, Long reviewId) {
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException("评价不存在");
        if (!Objects.equals(r.getUserId(), userId)) throw new BusinessException("无权操作他人评价");
        return r;
    }

    /**
     * 删除评价：清掉追评与屏蔽记录，评价本体保留墓碑（deleted = id）。
     * 唯一键为 (user_id, product_id, deleted)：
     * 有效评价 deleted=0 唯一；墓碑 deleted=id 互不冲突，用户可重新评价，
     * 种子数据里的 NOT EXISTS 也能看到墓碑，不会把用户删掉的评价重新塞回来。
     */
    private void cleanUpAndDelete(Long reviewId) {
        reviewAppendMapper.delete(new LambdaQueryWrapper<ReviewAppend>()
                .eq(ReviewAppend::getReviewId, reviewId));
        reviewHiddenMapper.deleteById(reviewId);
        jdbcTemplate.update("UPDATE review SET deleted = id WHERE id = ? AND deleted = 0", reviewId);
    }

    private Set<Long> hiddenIds() {
        return reviewHiddenMapper.selectList(null).stream()
                .map(ReviewHidden::getReviewId)
                .collect(Collectors.toSet());
    }

    private List<ReviewVO> toVOs(List<Review> reviews, Set<Long> hidden, boolean maskAnonymous) {
        if (reviews.isEmpty()) return List.of();

        Set<Long> userIds = reviews.stream().map(Review::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Set<Long> productIds = reviews.stream().map(Review::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        List<Long> reviewIds = reviews.stream().map(Review::getId).toList();
        Map<Long, List<ReviewAppendVO>> appendMap = reviewAppendMapper.selectList(
                        new LambdaQueryWrapper<ReviewAppend>()
                                .in(ReviewAppend::getReviewId, reviewIds)
                                .orderByAsc(ReviewAppend::getCreatedAt))
                .stream()
                .collect(Collectors.groupingBy(ReviewAppend::getReviewId,
                        Collectors.mapping(a -> ReviewAppendVO.builder()
                                .id(a.getId())
                                .content(a.getContent())
                                .createdAt(a.getCreatedAt())
                                .build(), Collectors.toList())));

        List<ReviewVO> res = new ArrayList<>();
        for (Review r : reviews) {
            User u = users.get(r.getUserId());
            Product p = products.get(r.getProductId());
            boolean anon = Integer.valueOf(1).equals(r.getAnonymous());
            boolean mask = anon && maskAnonymous;
            res.add(ReviewVO.builder()
                    .id(r.getId())
                    .userId(mask ? null : r.getUserId())
                    .username(mask ? "匿名用户"
                            : (u == null ? "匿名用户" : (u.getNickname() == null ? u.getUsername() : u.getNickname())))
                    .avatar(mask || u == null ? null : u.getAvatar())
                    .productId(r.getProductId())
                    .productName(p == null ? null : p.getName())
                    .productCover(p == null ? null : p.getCover())
                    .orderId(r.getOrderId())
                    .rating(r.getRating())
                    .content(r.getContent())
                    .anonymous(anon)
                    .createdAt(r.getCreatedAt())
                    .updatedAt(r.getUpdatedAt())
                    .hidden(hidden.contains(r.getId()))
                    .appends(appendMap.getOrDefault(r.getId(), List.of()))
                    .build());
        }
        return res;
    }

    /** 过滤关闭「公开我的评价」的用户，商品页不再展示其评价 */
    private void filterPrivate(List<Review> reviews) {
        Set<Long> ids = reviews.stream().map(Review::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        Map<Long, UserSetting> settings = settingMapper.selectList(new LambdaQueryWrapper<UserSetting>()
                        .in(UserSetting::getUserId, ids))
                .stream().collect(Collectors.toMap(UserSetting::getUserId, s -> s, (a, b) -> a));
        reviews.removeIf(r -> {
            UserSetting s = settings.get(r.getUserId());
            return s != null && Integer.valueOf(0).equals(s.getShowReviews());
        });
    }

    private int flag(Integer v) {
        return v != null && v == 1 ? 1 : 0;
    }
}

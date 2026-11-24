package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.entity.Coupon;
import com.ecommerce.entity.Orders;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.UserCoupon;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserCouponMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.service.ShopService;
import com.ecommerce.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final CategoryMapper categoryMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final ShopService shopService;

    /** 计入GMV的订单状态 */
    private static final List<String> PAID_STATUS = List.of("PAID", "SHIPPED", "COMPLETED");

    @Override
    public Map<String, Object> overview(Long shopId) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean scoped = shopId != null;

        data.put("scope", scoped ? "SHOP" : "PLATFORM");
        data.put("shopId", shopId);

        // 商家看板：用户数取本店下单用户数，分类数取本店商品覆盖的分类数
        Long userCount;
        Long categoryCount;
        if (scoped) {
            Set<Long> buyers = new HashSet<>();
            for (Orders o : orderMapper.selectList(new LambdaQueryWrapper<Orders>().eq(Orders::getShopId, shopId))) {
                if (o.getUserId() != null) buyers.add(o.getUserId());
            }
            userCount = (long) buyers.size();
            Set<Long> cats = new HashSet<>();
            for (Product p : productMapper.selectList(new LambdaQueryWrapper<Product>().eq(Product::getShopId, shopId))) {
                if (p.getCategoryId() != null) cats.add(p.getCategoryId());
            }
            categoryCount = (long) cats.size();
        } else {
            userCount = userMapper.selectCount(null);
            categoryCount = categoryMapper.selectCount(null);
        }
        data.put("userCount", userCount);
        data.put("categoryCount", categoryCount);

        data.put("productCount", productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(scoped, Product::getShopId, shopId)
                .eq(Product::getStatus, 1)));
        data.put("orderCount", orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(scoped, Orders::getShopId, shopId)));

        Map<String, Long> ordersCount = new LinkedHashMap<>();
        for (String s : List.of("PENDING_PAYMENT", "PAID", "SHIPPED", "COMPLETED", "CANCELLED")) {
            ordersCount.put(s, orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                    .eq(scoped, Orders::getShopId, shopId)
                    .eq(Orders::getStatus, s)));
        }
        data.put("ordersCount", ordersCount);

        // 今日销售额：以支付时间为准，统计已支付及之后的订单（不含取消）
        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        BigDecimal todaySales = sumAmount(orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(scoped, Orders::getShopId, shopId)
                .in(Orders::getStatus, PAID_STATUS)
                .between(Orders::getPaidAt, start, end)));
        data.put("todaySales", todaySales);

        // 累计销售额
        BigDecimal totalSales = sumAmount(orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(scoped, Orders::getShopId, shopId)
                .in(Orders::getStatus, PAID_STATUS)));
        data.put("totalSales", totalSales);

        // 库存预警数量
        Long lowStock = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(scoped, Product::getShopId, shopId)
                .le(Product::getStock, 10));
        data.put("lowStockCount", lowStock == null ? 0 : lowStock);

        // 优惠券按数据范围限定：商家看本店券，平台管理员看全部（含全平台券与各店券）
        int couponCount = Math.toIntExact(couponMapper.selectCount(new LambdaQueryWrapper<Coupon>()
                .eq(scoped, Coupon::getShopId, shopId)));
        long couponAvailable = couponMapper.selectCount(new LambdaQueryWrapper<Coupon>()
                .eq(scoped, Coupon::getShopId, shopId)
                .eq(Coupon::getStatus, 1));
        int couponUsed = Math.toIntExact(userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getStatus, "USED")
                .inSql(scoped, UserCoupon::getCouponId,
                        "SELECT id FROM coupon WHERE shop_id = " + shopId)));
        data.put("couponCount", couponCount);
        data.put("couponAvailable", couponAvailable);
        data.put("couponUsed", couponUsed);

        return data;
    }

    @Override
    public List<Map<String, Object>> salesTrend(int days, Long shopId) {
        int n = Math.max(1, Math.min(days, 30));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.atTime(LocalTime.MAX);
            List<Orders> list = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                    .eq(shopId != null, Orders::getShopId, shopId)
                    .in(Orders::getStatus, PAID_STATUS)
                    .between(Orders::getPaidAt, start, end));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("amount", sumAmount(list));
            point.put("count", list.size());
            trend.add(point);
        }
        return trend;
    }

    @Override
    public List<Map<String, Object>> lowStock(int threshold, Long shopId) {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(shopId != null, Product::getShopId, shopId)
                .le(Product::getStock, Math.max(0, threshold))
                .orderByAsc(Product::getStock)
                .last("LIMIT 20"));
        Map<Long, String> shopNames = shopService.nameMap(products.stream().map(Product::getShopId).toList());
        List<Map<String, Object>> res = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("shopName", shopNames.get(p.getShopId()));
            m.put("stock", p.getStock());
            m.put("sales", p.getSales());
            res.add(m);
        }
        return res;
    }

    @Override
    public List<Map<String, Object>> topProducts(int limit, Long shopId) {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(shopId != null, Product::getShopId, shopId)
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSales)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
        Map<Long, String> shopNames = shopService.nameMap(products.stream().map(Product::getShopId).toList());
        List<Map<String, Object>> res = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("shopName", shopNames.get(p.getShopId()));
            m.put("sales", p.getSales());
            m.put("stock", p.getStock());
            m.put("price", p.getPrice());
            res.add(m);
        }
        return res;
    }

    /** 销售额按实付统计：商品总额 - 优惠金额 */
    private BigDecimal sumAmount(List<Orders> orders) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Orders o : orders) {
            BigDecimal amount = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
            BigDecimal discount = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
            sum = sum.add(amount.subtract(discount));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}

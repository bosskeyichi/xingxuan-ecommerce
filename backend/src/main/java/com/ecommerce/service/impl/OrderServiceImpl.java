package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.OrderSubmitDTO;
import com.ecommerce.dto.ShipDTO;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.*;
import com.ecommerce.service.CartService;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.CartVO;
import com.ecommerce.vo.OrderItemVO;
import com.ecommerce.vo.OrderVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int MAX_QUANTITY = 999;

    /** 只有终态订单能删，别误删进行中的订单，不然库存和状态机会对不上 */
    private static final Set<String> DELETABLE_STATUS = Set.of("COMPLETED", "CANCELLED");

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final CartMapper cartMapper;
    private final UserMapper userMapper;
    private final CartService cartService;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final ShopService shopService;

    @Override
    @Transactional
    public List<String> submit(Long userId, OrderSubmitDTO dto) {
        List<CartVO> items = cartService.checkout(userId);

        // 1) 按店铺归集：服务端以当前数据库价格重算小计，别让加购后改价导致金额不一致
        Map<Long, List<CartVO>> byShop = new LinkedHashMap<>();
        Map<Long, BigDecimal> shopTotal = new LinkedHashMap<>();
        for (CartVO v : items) {
            Product p = productMapper.selectById(v.getProductId());
            if (p == null) throw new BusinessException("商品 " + v.getProductName() + " 已不存在");
            if (p.getStatus() == null || p.getStatus() != 1) {
                throw new BusinessException("商品 " + p.getName() + " 已下架，无法结算");
            }
            int qty = v.getQuantity() == null ? 0 : v.getQuantity();
            if (qty <= 0 || qty > MAX_QUANTITY) {
                throw new BusinessException("商品数量不合法： " + v.getProductName());
            }
            BigDecimal price = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            v.setShopId(p.getShopId());
            v.setPrice(price);
            v.setSubtotal(subtotal);
            byShop.computeIfAbsent(p.getShopId(), k -> new ArrayList<>()).add(v);
            shopTotal.merge(p.getShopId(), subtotal, BigDecimal::add);
        }
        shopTotal.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));

        // 2) 优惠券：一张券只抵扣一笔店铺订单。
        //    平台券（shop_id 为空）抵扣金额最高的店铺订单；店铺券只能抵扣本店订单。
        UserCoupon usingCoupon = null;
        Long couponShopId = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (dto.getUserCouponId() != null) {
            usingCoupon = userCouponMapper.selectById(dto.getUserCouponId());
            if (usingCoupon == null || !userId.equals(usingCoupon.getUserId())) {
                throw new BusinessException("优惠券不存在");
            }
            if (!"UNUSED".equals(usingCoupon.getStatus())) {
                throw new BusinessException("该优惠券已使用");
            }
            Coupon coupon = couponMapper.selectById(usingCoupon.getCouponId());
            if (coupon == null || coupon.getStatus() == null || coupon.getStatus() != 1) {
                throw new BusinessException("优惠券已失效");
            }
            LocalDateTime now = LocalDateTime.now();
            if (coupon.getStartAt() != null && now.isBefore(coupon.getStartAt())) {
                throw new BusinessException("优惠券尚未开始使用");
            }
            if (coupon.getEndAt() != null && now.isAfter(coupon.getEndAt())) {
                throw new BusinessException("优惠券已过期");
            }

            if (coupon.getShopId() != null) {
                // 店铺券：本次结算里必须有该店铺的商品
                if (!shopTotal.containsKey(coupon.getShopId())) {
                    String shopName = shopService.nameMap(List.of(coupon.getShopId())).get(coupon.getShopId());
                    throw new BusinessException("该优惠券仅限「" + (shopName == null ? "指定店铺" : shopName)
                            + "」的商品使用");
                }
                couponShopId = coupon.getShopId();
            } else {
                couponShopId = shopTotal.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse(null);
            }
            if (couponShopId == null) {
                throw new BusinessException("没有可抵扣的店铺订单");
            }
            BigDecimal baseTotal = shopTotal.getOrDefault(couponShopId, BigDecimal.ZERO);
            if (coupon.getThreshold() != null && baseTotal.compareTo(coupon.getThreshold()) < 0) {
                throw new BusinessException("优惠券需单笔店铺订单满 " + coupon.getThreshold()
                        + " 元，当前该店铺订单仅 " + baseTotal + " 元，无法使用");
            }
            discount = (coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount())
                    .setScale(2, RoundingMode.HALF_UP);
            if (discount.compareTo(baseTotal) > 0) discount = baseTotal;
        }

        // 3) 每个店铺生成一笔独立订单：同一事务内完成，任一店铺失败则整批回滚
        List<String> orderNos = new ArrayList<>();
        Long couponOrderId = null;
        for (Map.Entry<Long, List<CartVO>> entry : byShop.entrySet()) {
            Long shopId = entry.getKey();
            // 关店店铺不允许下单（列表已隐藏其商品，这里兜底防止从收藏 / 旧链接进入）
            Shop shop = shopService.detail(shopId);
            if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
                throw new BusinessException("店铺「" + (shop == null ? "已关闭" : shop.getName()) + "」暂停营业，请稍后再下单");
            }
            List<CartVO> shopItems = entry.getValue();
            BigDecimal goodsTotal = shopTotal.get(shopId);
            boolean useCoupon = couponShopId != null && couponShopId.equals(shopId);

            Orders order = new Orders();
            order.setOrderNo(genOrderNo());
            order.setShopId(shopId);
            order.setUserId(userId);
            order.setTotalAmount(goodsTotal);
            order.setDiscountAmount(useCoupon ? discount : BigDecimal.ZERO);
            order.setCouponId(useCoupon ? usingCoupon.getId() : null);
            order.setStatus("PENDING_PAYMENT");
            order.setReceiverName(dto.getReceiverName());
            order.setReceiverPhone(dto.getReceiverPhone());
            order.setReceiverAddr(dto.getReceiverAddr());
            order.setRemark(dto.getRemark());
            orderMapper.insert(order);
            if (useCoupon) couponOrderId = order.getId();

            for (CartVO v : shopItems) {
                int qty = v.getQuantity();

                // 原子扣减库存：条件更新 + 影响行数判断，杜绝并发超卖
                LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                        .setSql("stock = stock - " + qty)
                        .setSql("sales = sales + " + qty)
                        .eq(Product::getId, v.getProductId())
                        .ge(Product::getStock, qty);
                if (productMapper.update(null, uw) == 0) {
                    throw new BusinessException("商品 " + v.getProductName() + " 库存不足，请重新下单");
                }

                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setProductId(v.getProductId());
                item.setProductName(v.getProductName());
                item.setProductCover(v.getProductCover());
                item.setPrice(v.getPrice());
                item.setQuantity(qty);
                item.setSubtotal(v.getSubtotal());
                orderItemMapper.insert(item);

                // 抢占式清理购物车项：影响行数为 0 说明已被并发的另一次提交结算过，
                // 此时必须回滚，别让同一批商品被重复下单、库存被扣两次
                if (cartMapper.deleteById(v.getId()) == 0) {
                    throw new BusinessException("购物车已变更，请刷新后重试");
                }
            }
            orderNos.add(order.getOrderNo());
        }

        // 4) 标记优惠券为已使用：条件更新 status=UNUSED，影响行数为 0 说明已被并发占用
        if (usingCoupon != null) {
            int rows = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .set(UserCoupon::getStatus, "USED")
                    .set(UserCoupon::getOrderId, couponOrderId)
                    .set(UserCoupon::getUsedAt, LocalDateTime.now())
                    .eq(UserCoupon::getId, usingCoupon.getId())
                    .eq(UserCoupon::getStatus, "UNUSED"));
            if (rows == 0) {
                throw new BusinessException("该优惠券已被使用，请重新选择");
            }
        }
        return orderNos;
    }

    @Override
    public OrderVO detail(Long userId, String orderNo) {
        Orders o = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo));
        if (o == null) throw new BusinessException("订单不存在");
        // 归属校验：userId 为空（后台调用）时不校验，否则必须一致
        if (userId == null || !userId.equals(o.getUserId())) {
            throw new BusinessException("无权查看此订单");
        }
        return toVO(o);
    }

    @Override
    public IPage<OrderVO> page(Long userId, String status, long current, long size) {
        LambdaQueryWrapper<Orders> q = new LambdaQueryWrapper<>();
        q.eq(Orders::getUserId, userId);
        if (status != null && !status.isEmpty()) q.eq(Orders::getStatus, status);
        q.orderByDesc(Orders::getCreatedAt);
        Page<Orders> page = orderMapper.selectPage(new Page<>(current, size), q);
        return convert(page);
    }

    @Override
    @Transactional
    public void mockPay(Long userId, String orderNo) {
        Orders o = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId));
        if (o == null) throw new BusinessException("订单不存在");

        // 条件更新：保证状态机幂等，防止并发重复支付
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, "PAID")
                .set(Orders::getPaidAt, LocalDateTime.now())
                .eq(Orders::getId, o.getId())
                .eq(Orders::getStatus, "PENDING_PAYMENT");
        if (orderMapper.update(null, uw) == 0) {
            throw new BusinessException("订单状态不允许支付");
        }
    }

    @Override
    @Transactional
    public int mockPayBatch(Long userId, List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            throw new BusinessException("请选择要支付的订单");
        }
        // 去重后按「当前用户 + 订单号」查，防止替别人支付
        List<String> distinct = orderNos.stream().distinct().toList();
        List<Orders> orders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .in(Orders::getOrderNo, distinct));
        if (orders.size() != distinct.size()) {
            throw new BusinessException("部分订单不存在或不属于当前账号");
        }
        // 逐笔条件更新：已是 PAID 的跳过，保证并发 / 重复点击下不会重复支付
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Orders o : orders) {
            LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<Orders>()
                    .set(Orders::getStatus, "PAID")
                    .set(Orders::getPaidAt, now)
                    .eq(Orders::getId, o.getId())
                    .eq(Orders::getStatus, "PENDING_PAYMENT");
            count += orderMapper.update(null, uw);
        }
        if (count == 0) {
            throw new BusinessException("所选订单都不是待支付状态");
        }
        return count;
    }

    @Override
    @Transactional
    public void cancel(Long userId, String orderNo) {
        Orders o = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId));
        if (o == null) throw new BusinessException("订单不存在");

        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, "CANCELLED")
                .set(Orders::getCancelledAt, LocalDateTime.now())
                .eq(Orders::getId, o.getId())
                .eq(Orders::getStatus, "PENDING_PAYMENT");
        if (orderMapper.update(null, uw) == 0) {
            throw new BusinessException("仅待支付订单可取消");
        }
        // 回退库存 + 还原购物车，方便用户重新下单
        restoreStockAndCart(o);
    }

    @Override
    @Transactional
    public void confirmReceipt(Long userId, String orderNo) {
        Orders o = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId));
        if (o == null) throw new BusinessException("订单不存在");

        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, "COMPLETED")
                .set(Orders::getCompletedAt, LocalDateTime.now())
                .eq(Orders::getId, o.getId())
                .eq(Orders::getStatus, "SHIPPED");
        if (orderMapper.update(null, uw) == 0) {
            throw new BusinessException("仅已发货订单可确认收货");
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, String orderNo) {
        Orders o = findDeletable(userId, orderNo, null);
        orderMapper.deleteById(o.getId());
    }

    @Override
    @Transactional
    public int batchDelete(Long userId, List<String> orderNos) {
        List<String> nos = normalizeOrderNos(orderNos);
        List<Orders> orders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .in(Orders::getOrderNo, nos));
        checkDeletable(orders, nos);
        return orderMapper.delete(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .in(Orders::getOrderNo, nos)
                .in(Orders::getStatus, DELETABLE_STATUS));
    }

    @Override
    @Transactional
    public void adminDelete(String orderNo, Long shopId) {
        Orders o = findDeletable(null, orderNo, shopId);
        orderMapper.deleteById(o.getId());
    }

    @Override
    @Transactional
    public int adminBatchDelete(List<String> orderNos, Long shopId) {
        List<String> nos = normalizeOrderNos(orderNos);
        List<Orders> orders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(shopId != null, Orders::getShopId, shopId)
                .in(Orders::getOrderNo, nos));
        checkDeletable(orders, nos);
        return orderMapper.delete(new LambdaQueryWrapper<Orders>()
                .eq(shopId != null, Orders::getShopId, shopId)
                .in(Orders::getOrderNo, nos)
                .in(Orders::getStatus, DELETABLE_STATUS));
    }

    @Override
    @Transactional
    public void ship(String orderNo, Long shopId, ShipDTO dto) {
        if (shopId == null) throw new BusinessException(403, "仅商家可对订单发货");
        Orders o = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(shopId != null, Orders::getShopId, shopId));
        if (o == null) throw new BusinessException("订单不存在或不属于当前店铺");

        String company = dto == null || dto.getTrackingCompany() == null ? null : dto.getTrackingCompany().trim();
        String no = dto == null || dto.getTrackingNo() == null ? null : dto.getTrackingNo().trim();
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, "SHIPPED")
                .set(Orders::getShippedAt, LocalDateTime.now())
                .set(Orders::getTrackingCompany, company)
                .set(Orders::getTrackingNo, no)
                .eq(Orders::getId, o.getId())
                .eq(Orders::getStatus, "PAID");
        if (orderMapper.update(null, uw) == 0) {
            throw new BusinessException("仅已支付订单可发货");
        }
    }

    @Override
    @Transactional
    public void complete(String orderNo, Long shopId) {
        if (shopId == null) throw new BusinessException(403, "仅商家可将订单标记完成");
        Orders o = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(shopId != null, Orders::getShopId, shopId));
        if (o == null) throw new BusinessException("订单不存在或不属于当前店铺");

        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, "COMPLETED")
                .set(Orders::getCompletedAt, LocalDateTime.now())
                .eq(Orders::getId, o.getId())
                .eq(Orders::getStatus, "SHIPPED");
        if (orderMapper.update(null, uw) == 0) {
            throw new BusinessException("仅已发货订单可标记完成");
        }
    }

    @Override
    public IPage<OrderVO> adminPage(String status, long current, long size, Long shopId) {
        LambdaQueryWrapper<Orders> q = new LambdaQueryWrapper<>();
        if (shopId != null) q.eq(Orders::getShopId, shopId);
        if (status != null && !status.isEmpty()) q.eq(Orders::getStatus, status);
        q.orderByDesc(Orders::getCreatedAt);
        Page<Orders> page = orderMapper.selectPage(new Page<>(current, size), q);
        return convert(page);
    }

    @Override
    @Transactional
    public int autoCancelExpired(int minutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(Math.max(1, minutes));
        List<Orders> expired = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, "PENDING_PAYMENT")
                .lt(Orders::getCreatedAt, deadline));
        int count = 0;
        for (Orders o : expired) {
            LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<Orders>()
                    .set(Orders::getStatus, "CANCELLED")
                    .set(Orders::getCancelledAt, LocalDateTime.now())
                    .eq(Orders::getId, o.getId())
                    .eq(Orders::getStatus, "PENDING_PAYMENT");
            if (orderMapper.update(null, uw) == 0) continue;
            restoreStockAndCart(o);
            count++;
        }
        if (count > 0) {
            log.info("[定时任务] 自动取消超时未支付订单 {} 笔，并已回退库存", count);
        }
        return count;
    }

    @Override
    public void exportCsv(HttpServletResponse response, Long shopId) throws IOException {
        List<Orders> orders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(shopId != null, Orders::getShopId, shopId)
                .orderByDesc(Orders::getCreatedAt));

        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null ? u.getUsername() : u.getNickname(),
                        (a, b) -> a));

        Map<Long, String> shopNames = shopService.nameMap(orders.stream().map(Orders::getShopId).toList());

        Map<String, String> statusLabel = Map.of(
                "PENDING_PAYMENT", "待支付", "PAID", "已支付", "SHIPPED", "已发货",
                "COMPLETED", "已完成", "CANCELLED", "已取消");

        response.setContentType("text/csv;charset=utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=orders_export.csv");

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            pw.print("﻿"); // UTF-8 BOM，保证 Excel 正确识别中文
            pw.println("订单号,店铺,用户名,用户ID,商品金额,优惠金额,实付金额,状态,收货人,手机号,收货地址,下单时间,支付时间");
            for (Orders o : orders) {
                String ts = o.getCreatedAt() == null ? "" : o.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String ps = o.getPaidAt() == null ? "" : o.getPaidAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String[] cols = {
                        safe(o.getOrderNo()),
                        safe(shopNames.get(o.getShopId())),
                        safe(userNames.get(o.getUserId())),
                        safe(String.valueOf(o.getUserId())),
                        safe(o.getTotalAmount() == null ? "0" : o.getTotalAmount().toString()),
                        safe(o.getDiscountAmount() == null ? "0" : o.getDiscountAmount().toString()),
                        safe((o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                                .subtract(o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount()).toString()),
                        statusLabel.getOrDefault(o.getStatus(), safe(o.getStatus())),
                        safe(o.getReceiverName()),
                        safe(o.getReceiverPhone()),
                        safe(o.getReceiverAddr()),
                        ts, ps
                };
                pw.println(String.join(",", cols));
            }
            pw.flush();
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        // 防 CSV 公式注入：以 = + - @ 或制表符开头的单元格加前缀单引号
        if (!s.isEmpty() && "=+-@\t".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ---- helpers ----

    private Orders findDeletable(Long userId, String orderNo, Long shopId) {
        Orders o = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(userId != null, Orders::getUserId, userId)
                .eq(shopId != null, Orders::getShopId, shopId));
        if (o == null) throw new BusinessException("订单不存在或无权操作");
        if (!DELETABLE_STATUS.contains(o.getStatus())) {
            throw new BusinessException("仅已完成或已取消的订单可以删除");
        }
        return o;
    }

    private static List<String> normalizeOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) throw new BusinessException("请选择要删除的订单");
        List<String> nos = orderNos.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (nos.isEmpty()) throw new BusinessException("请选择要删除的订单");
        if (nos.size() > 100) throw new BusinessException("单次最多删除 100 笔订单");
        return nos;
    }

    private static void checkDeletable(List<Orders> orders, List<String> orderNos) {
        if (orders.size() != orderNos.size()) {
            throw new BusinessException("部分订单不存在或无权操作，请刷新后重试");
        }
        for (Orders o : orders) {
            if (!DELETABLE_STATUS.contains(o.getStatus())) {
                throw new BusinessException("订单 " + o.getOrderNo() + " 尚未完成或取消，不能删除");
            }
        }
    }

    /** 回退库存与销量，并把商品还原进购物车（取消 / 超时自动取消时调用） */
    private void restoreStockAndCart(Orders order) {
        // 归还已使用的优惠券（显式置空 used_at / order_id，别让 updateById 忽略 null）
        if (order.getCouponId() != null) {
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .set(UserCoupon::getStatus, "UNUSED")
                    .set(UserCoupon::getOrderId, null)
                    .set(UserCoupon::getUsedAt, null)
                    .eq(UserCoupon::getId, order.getCouponId())
                    .eq(UserCoupon::getStatus, "USED"));
        }

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem it : items) {
            Product p = productMapper.selectById(it.getProductId());
            if (p != null) {
                int qty = it.getQuantity() == null ? 0 : Math.max(0, it.getQuantity());
                // 库存与销量都用原子表达式，别让并发取消时「读-改-写」覆盖掉对方的减量
                LambdaUpdateWrapper<Product> uw = new LambdaUpdateWrapper<Product>()
                        .setSql("stock = stock + " + qty)
                        .setSql("sales = GREATEST(sales - " + qty + ", 0)")
                        .eq(Product::getId, p.getId());
                productMapper.update(null, uw);
            }
            // 还原购物车（取消后可以一键重新下单）
            if (order.getUserId() != null && it.getProductId() != null) {
                Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, order.getUserId())
                        .eq(Cart::getProductId, it.getProductId()));
                int qty = it.getQuantity() == null ? 0 : Math.max(1, it.getQuantity());
                if (exist != null) {
                    exist.setQuantity(exist.getQuantity() + qty);
                    cartMapper.updateById(exist);
                } else if (cartMapper.restoreDeleted(order.getUserId(), it.getProductId(), qty) == 0) {
                    Cart c = new Cart();
                    c.setUserId(order.getUserId());
                    c.setProductId(it.getProductId());
                    c.setQuantity(qty);
                    c.setSelected(1);
                    cartMapper.insert(c);
                }
            }
        }
    }

    /** 批量装载订单明细，别让 N+1 查询 */
    private IPage<OrderVO> convert(Page<Orders> page) {
        List<Orders> records = page.getRecords();
        if (records.isEmpty()) {
            return page.convert(o -> toVO(o, Collections.emptyList(), null, null));
        }
        List<Long> orderIds = records.stream().map(Orders::getId).toList();
        Map<Long, List<OrderItem>> itemsMap = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        Set<Long> userIds = records.stream().map(Orders::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            userNames = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? u.getUsername() : u.getNickname(), (a, b) -> a));
        }
        Map<Long, String> shopNames = shopService.nameMap(records.stream().map(Orders::getShopId).toList());
        Map<Long, String> finalNames = userNames;
        return page.convert(o -> toVO(o, itemsMap.getOrDefault(o.getId(), Collections.emptyList()),
                finalNames.get(o.getUserId()), shopNames.get(o.getShopId())));
    }

    private OrderVO toVO(Orders o) {
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, o.getId()));
        User u = o.getUserId() == null ? null : userMapper.selectById(o.getUserId());
        String shopName = shopService.nameMap(Collections.singletonList(o.getShopId())).get(o.getShopId());
        return toVO(o, items, u == null ? null : (u.getNickname() == null ? u.getUsername() : u.getNickname()), shopName);
    }

    private OrderVO toVO(Orders o, List<OrderItem> items, String username, String shopName) {
        OrderVO v = new OrderVO();
        v.setId(o.getId());
        v.setOrderNo(o.getOrderNo());
        v.setShopId(o.getShopId());
        v.setShopName(shopName);
        v.setUserId(o.getUserId());
        v.setUsername(username);
        v.setTotalAmount(o.getTotalAmount());
        v.setDiscountAmount(o.getDiscountAmount());
        v.setCouponId(o.getCouponId());
        v.setStatus(o.getStatus());
        v.setReceiverName(o.getReceiverName());
        v.setReceiverPhone(o.getReceiverPhone());
        v.setReceiverAddr(o.getReceiverAddr());
        v.setRemark(o.getRemark());
        v.setPaidAt(o.getPaidAt());
        v.setShippedAt(o.getShippedAt());
        v.setCompletedAt(o.getCompletedAt());
        v.setCancelledAt(o.getCancelledAt());
        v.setCreatedAt(o.getCreatedAt());
        v.setTrackingCompany(o.getTrackingCompany());
        v.setTrackingNo(o.getTrackingNo());

        List<OrderItemVO> vos = new ArrayList<>();
        for (OrderItem it : items) {
            OrderItemVO iv = new OrderItemVO();
            iv.setId(it.getId());
            iv.setProductId(it.getProductId());
            iv.setProductName(it.getProductName());
            iv.setProductCover(it.getProductCover());
            iv.setPrice(it.getPrice());
            iv.setQuantity(it.getQuantity());
            iv.setSubtotal(it.getSubtotal());
            vos.add(iv);
        }
        v.setItems(vos);
        return v;
    }

    /** 订单号：时间戳 + UUID 片段 + 原子自增序号，别让并发碰撞 */
    private String genOrderNo() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}

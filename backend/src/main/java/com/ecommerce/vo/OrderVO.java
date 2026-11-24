package com.ecommerce.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long shopId;
    private String shopName;
    private Long userId;
    private String username;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private Long couponId;
    /** PENDING_PAYMENT / PAID / SHIPPED / COMPLETED / CANCELLED */
    private String status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddr;
    private String remark;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String trackingCompany;
    private String trackingNo;
    private LocalDateTime createdAt;
    private List<OrderItemVO> items;
}
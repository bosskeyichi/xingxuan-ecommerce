package com.ecommerce.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserCouponVO {
    private Long id;            // user_coupon 主键
    private Long couponId;      // coupon 主键
    private String name;
    private String description;
    private BigDecimal threshold;
    private BigDecimal discount;
    /** 适用店铺：null = 全平台券 */
    private Long shopId;
    private String shopName;
    private String status;      // UNUSED / USED
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime usedAt;
    private LocalDateTime claimTime;   // 领取时间 = user_coupon.created_at
}

package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCoupon implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long couponId;
    /** UNUSED 未使用 / USED 已使用 */
    private String status;
    private Long orderId;
    private LocalDateTime usedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

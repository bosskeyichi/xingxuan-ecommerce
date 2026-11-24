package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商品评价 */
@Data
@TableName("review")
public class Review implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long productId;
    private Long orderId;

    /** 1-5 星 */
    private Integer rating;

    private String content;

    /** 1 匿名评价（商品页隐藏评价人），0 实名 */
    private Integer anonymous;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

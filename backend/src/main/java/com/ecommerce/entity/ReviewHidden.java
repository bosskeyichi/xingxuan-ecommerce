package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 被管理员屏蔽的评价（软屏蔽，可恢复） */
@Data
@TableName("review_hidden")
public class ReviewHidden implements Serializable {

    @TableId(value = "review_id", type = IdType.INPUT)
    private Long reviewId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

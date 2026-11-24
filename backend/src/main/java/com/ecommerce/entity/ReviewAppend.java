package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 商品追评 */
@Data
@TableName("review_append")
public class ReviewAppend implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

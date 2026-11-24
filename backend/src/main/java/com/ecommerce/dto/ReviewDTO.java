package com.ecommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewDTO {

    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    /** 关联的订单号（可选，用来标记"已购买"评价） */
    private String orderNo;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围为 1-5 星")
    @Max(value = 5, message = "评分范围为 1-5 星")
    private Integer rating;

    @Size(max = 500, message = "评价内容不能超过 500 个字符")
    private String content;

    /** 1 匿名评价 / 0 实名 */
    private Integer anonymous;
}

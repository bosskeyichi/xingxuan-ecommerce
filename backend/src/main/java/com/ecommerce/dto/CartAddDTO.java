package com.ecommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartAddDTO {

    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    @Min(value = 1, message = "购买数量必须大于 0")
    @Max(value = 999, message = "单件商品最多购买 999 件")
    private Integer quantity = 1;
}

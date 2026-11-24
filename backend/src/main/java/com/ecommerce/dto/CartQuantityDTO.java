package com.ecommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartQuantityDTO {

    @NotNull(message = "购物车项 ID 不能为空")
    private Long cartId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "购买数量必须大于 0")
    @Max(value = 999, message = "单件商品最多购买 999 件")
    private Integer quantity;
}

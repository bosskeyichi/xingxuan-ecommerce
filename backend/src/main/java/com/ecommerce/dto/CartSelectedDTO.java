package com.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartSelectedDTO {

    @NotNull(message = "购物车项 ID 不能为空")
    private Long cartId;

    /** 1 选中 / 0 取消选中 */
    @NotNull(message = "选中状态不能为空")
    private Integer selected;
}

package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShopDTO {

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 60, message = "店铺名称不能超过 60 个字符")
    private String name;

    @Size(max = 200, message = "店铺简介不能超过 200 个字符")
    private String intro;

    private String cover;
}

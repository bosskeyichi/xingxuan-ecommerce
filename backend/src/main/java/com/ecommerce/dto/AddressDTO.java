package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressDTO {

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50, message = "收货人姓名过长")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String receiverPhone;

    @NotBlank(message = "所在地区不能为空")
    @Size(max = 100, message = "所在地区过长")
    private String region;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址过长")
    private String detail;

    /** 1 = 设为默认地址 */
    private Integer isDefault;
}

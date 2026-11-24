package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderSubmitDTO {

    @NotBlank(message = "收货人姓名不能为空")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddr;

    private String remark;

    /** 使用的用户优惠券 id（可选） */
    private Long userCouponId;
}
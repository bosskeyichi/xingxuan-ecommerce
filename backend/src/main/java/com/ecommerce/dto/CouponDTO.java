package com.ecommerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponDTO {
    /** 适用店铺：管理员可指定店铺，null 表示全平台券；商家创建时忽略该值，强制为本店 */
    private Long shopId;

    @NotBlank(message = "优惠券名称不能为空")
    @Size(max = 50, message = "优惠券名称不能超过 50 个字符")
    private String name;

    @Size(max = 200, message = "优惠券说明不能超过 200 个字符")
    private String description;

    @NotNull(message = "使用门槛不能为空")
    @DecimalMin(value = "0.00", message = "使用门槛不能为负数")
    private BigDecimal threshold;

    @NotNull(message = "减免金额不能为空")
    @DecimalMin(value = "0.01", message = "减免金额必须大于 0")
    private BigDecimal discount;

    @NotNull(message = "发行总量不能为空")
    @Min(value = 0, message = "发行总量不能为负数")
    private Integer total;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
}

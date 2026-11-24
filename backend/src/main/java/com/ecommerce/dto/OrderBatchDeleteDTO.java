package com.ecommerce.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 订单批量删除入参 */
@Data
public class OrderBatchDeleteDTO {

    @NotEmpty(message = "请选择要删除的订单")
    @Size(max = 100, message = "单次最多删除 100 笔订单")
    private List<String> orderNos;
}

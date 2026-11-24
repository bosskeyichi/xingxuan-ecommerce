package com.ecommerce.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 订单合并支付入参（跨店结算会生成多笔订单，支付动作可一次完成） */
@Data
public class OrderBatchPayDTO {

    @NotEmpty(message = "请选择要支付的订单")
    @Size(max = 50, message = "单次最多合并支付 50 笔订单")
    private List<String> orderNos;
}

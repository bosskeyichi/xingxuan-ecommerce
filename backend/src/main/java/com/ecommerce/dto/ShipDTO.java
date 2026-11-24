package com.ecommerce.dto;

import lombok.Data;

/** 发货参数：快递公司与快递单号（均可选，建议填写以便用户查询物流） */
@Data
public class ShipDTO {
    private String trackingCompany;
    private String trackingNo;
}

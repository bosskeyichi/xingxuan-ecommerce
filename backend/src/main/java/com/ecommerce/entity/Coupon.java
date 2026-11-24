package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class Coupon implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 适用店铺：null = 全平台券，非空 = 仅该店铺的订单可用 */
    private Long shopId;

    /** 店铺名称，仅用来列表展示，不入库 */
    @TableField(exist = false)
    private String shopName;

    private String name;
    private String description;
    private BigDecimal threshold;   // 满 threshold 元可用
    private BigDecimal discount;    // 减免金额
    private Integer total;          // 发行总量
    private Integer claimed;        // 已领取数量
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;         // 1 上架 / 0 下架

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

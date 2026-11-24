package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "`orders`")
public class Orders implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 归属店铺（跨店结算时按店铺拆单） */
    private Long shopId;

    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private Long couponId;
    private String status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddr;
    private String remark;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    /** 快递公司（发货时填写，便于用户查询物流） */
    private String trackingCompany;
    /** 快递单号 */
    private String trackingNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
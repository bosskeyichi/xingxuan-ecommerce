package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货地址簿
 */
@Data
@TableName("address")
public class Address implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String receiverName;
    private String receiverPhone;
    /** 所在地区（省市区） */
    private String region;
    /** 详细地址 */
    private String detail;
    /** 1 = 默认地址 */
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 店铺（商家）：一个 MERCHANT 账号对应一个店铺，商品与订单都归属到店铺。
 */
@Data
@TableName("shop")
public class Shop implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店主账号 ID（user_table.id） */
    private Long ownerId;

    private String name;
    private String intro;
    private String cover;

    /** 1 营业 / 0 关店 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

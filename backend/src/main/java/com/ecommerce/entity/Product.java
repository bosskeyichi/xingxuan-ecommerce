package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属店铺 */
    private Long shopId;

    private String name;
    private Long categoryId;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private String cover;
    private String description;

    /** 搜索别名（逗号分隔）：商品名里没有的品类词写这里，如「鞋子,运动鞋」 */
    private String keywords;

    /** 1 包邮 / 0 需另付运费（店铺可自行维护的服务承诺） */
    private Integer freeShipping;

    /** 承诺发货时效（小时），0 表示不承诺 */
    private Integer shipHours;

    /** 无理由退换天数，0 表示不支持 */
    private Integer returnDays;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 非数据库字段：列表 / 详情展示用 */
    @TableField(exist = false)
    private String shopName;

    /** 非数据库字段：所属店铺状态（1 营业 / 0 关店），详情页据此禁用加购 */
    @TableField(exist = false)
    private Integer shopStatus;

    @TableLogic
    private Integer deleted;
}
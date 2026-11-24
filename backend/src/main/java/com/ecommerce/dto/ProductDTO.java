package com.ecommerce.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductDTO {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称不能超过 100 个字符")
    private String name;

    private Long categoryId;

    /** 所属店铺：由后端按操作人角色决定（商家=本店，超管=自己店铺），前端传值不再生效 */
    private Long shopId;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    private String cover;

    @Size(max = 500, message = "描述不能超过 500 个字符")
    private String description;

    /** 搜索别名（逗号分隔）：商品名里没有的品类词写这里，如「鞋子,运动鞋」 */
    @Size(max = 200, message = "搜索别名不能超过 200 个字符")
    private String keywords;

    /** 1 包邮 / 0 不包邮 */
    private Integer freeShipping;

    /** 承诺发货时效（小时），0 表示不承诺 */
    @Min(value = 0, message = "发货时效不能为负数")
    private Integer shipHours;

    /** 无理由退换天数，0 表示不支持 */
    @Min(value = 0, message = "无理由天数不能为负数")
    private Integer returnDays;

    /** 1 上架 / 0 下架 */
    private Integer status;
}

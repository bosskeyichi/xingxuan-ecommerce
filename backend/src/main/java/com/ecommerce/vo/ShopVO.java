package com.ecommerce.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 店铺视图：后台店铺列表用，附带商品数 / 订单数 / 成交额。
 */
@Data
public class ShopVO {
    private Long id;
    private Long ownerId;
    private String ownerName;
    /** 店主登录账号，页面显示「昵称 @账号」，别让多个昵称相同无法区分 */
    private String ownerUsername;
    private String name;
    private String intro;
    private Integer status;
    private Long productCount;
    private Long orderCount;
    private BigDecimal salesAmount;
}

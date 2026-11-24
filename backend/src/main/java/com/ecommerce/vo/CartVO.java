package com.ecommerce.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartVO {
    private Long id;
    private Long userId;
    private Long productId;
    /** 商品所属店铺，购物车按店铺分组 */
    private Long shopId;
    private String shopName;
    private String productName;
    private String productCover;
    private BigDecimal price;
    private Integer stock;
    private Integer quantity;
    private Integer selected;
    private BigDecimal subtotal;
}
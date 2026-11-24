package com.ecommerce.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewVO {
    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private Long productId;
    /** 商品名与封面：我的评价列表与分享卡片使用 */
    private String productName;
    private String productCover;
    private Long orderId;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 是否匿名评价 */
    private Boolean anonymous;
    /** 是否被管理员屏蔽（商品页不展示，用户自己的订单里会提示） */
    private Boolean hidden;
    /** 追评，按时间正序 */
    private List<ReviewAppendVO> appends;
}

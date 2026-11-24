package com.ecommerce.vo;

import lombok.Data;

/**
 * 单条消息。分享卡片（PRODUCT / SHOP / REVIEW）会补 targetTitle / targetSubtitle / targetCover，
 * 前端无需再逐条回查详情。
 */
@Data
public class MessageVO {

    private Long id;
    private String scene;
    private Long fromId;
    private Long toId;

    /** TEXT / PRODUCT / SHOP / REVIEW */
    private String type;
    private String content;

    private Long targetId;
    /** 点击卡片跳转用的 id：商品 = 商品 id，店铺 = 店铺 id，评价 = 所属商品 id */
    private Long targetLinkId;
    private String targetTitle;
    private String targetSubtitle;
    private String targetCover;

    private Long shopId;
    private String shopName;

    private String createdAt;
    private String time;

    /** 是否我发出的 */
    private Boolean mine;

    private String fromName;
    private String fromAvatar;
}

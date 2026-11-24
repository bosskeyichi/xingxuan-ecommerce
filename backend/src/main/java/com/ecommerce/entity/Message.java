package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私聊消息。
 * scene=FRIEND：好友之间聊天；scene=SERVICE：用户向店铺客服（店主）咨询。
 * type=TEXT 普通文本；PRODUCT / SHOP / REVIEW 为分享卡片，target_id 指向对应业务数据。
 * from_id = 0 表示系统通知（如举报处理结果）。
 */
@Data
@TableName("message")
public class Message implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 较小 userId : 较大 userId : scene */
    private String conversationKey;

    /** FRIEND / SERVICE */
    private String scene;

    private Long fromId;

    private Long toId;

    /** TEXT / PRODUCT / SHOP / REVIEW */
    private String type;

    private String content;

    /** 分享的商品 / 店铺 / 评价 id */
    private Long targetId;

    /** 客服会话所属店铺 */
    private Long shopId;

    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

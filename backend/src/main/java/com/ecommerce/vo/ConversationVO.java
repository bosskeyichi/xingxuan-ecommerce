package com.ecommerce.vo;

import lombok.Data;

/**
 * 会话摘要：好友会话与客服会话统一返回，前端按 scene 分 tab。
 */
@Data
public class ConversationVO {

    /** 会话标识：minId:maxId:scene */
    private String key;

    /** FRIEND / SERVICE */
    private String scene;

    private Long peerId;
    private String peerName;
    private String peerHandle;
    private String peerAvatar;

    private String lastContent;
    /** 展示用的简短时间 */
    private String lastTime;
    /** 排序用的完整时间（无消息时为空） */
    private String lastAt;

    /** 该会话未读数 */
    private Integer unread;

    /** 客服会话的店铺 */
    private Long shopId;
    private String shopName;

    /** 是否好友（客服会话的对方可能不是好友） */
    private Boolean friend;
}

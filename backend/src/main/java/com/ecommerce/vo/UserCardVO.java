package com.ecommerce.vo;

import lombok.Data;

/**
 * 用户卡片：搜索结果、好友列表、会话对方信息共用。
 * email / phone 仅在对方设置允许展示时返回，否则为 null。
 */
@Data
public class UserCardVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;

    /** 商家对应店铺 */
    private Long shopId;
    private String shopName;

    /** 是否已是好友 */
    private Boolean friend;

    /** 是否为系统默认好友（超级管理员 ↔ 商家），不可删除 */
    private Boolean system;

    /** 是否已向对方发出好友申请且还在等待处理 */
    private Boolean pending;

    /** 是否是自己 */
    private Boolean self;

    /** 是否可添加（对方关闭搜索 / 不允许加好友时为 false） */
    private Boolean addable;

    private String email;
    private String phone;

    /** 最近活跃时间（基于会话 / 登录，可为空） */
    private String lastActiveAt;
}

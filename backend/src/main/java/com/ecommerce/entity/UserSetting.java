package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 个人设置：隐私（能否被搜索 / 谁能加好友 / 展示邮箱手机号 / 是否公开评价）
 * 与消息（好友、客服、系统消息接收开关与提示音）。
 * 一个账号一行，未初始化时后端按默认值返回。
 */
@Data
@TableName("user_setting")
public class UserSetting implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 允许通过昵称 / 用户名 / ID 被搜索到 */
    private Integer searchable;

    /** ALL 所有人可加 / NOBODY 不允许 */
    private String friendPolicy;

    private Integer showEmail;
    private Integer showPhone;

    /** 允许他人查看我的评价 */
    private Integer showReviews;

    private Integer receiveFriendMsg;
    private Integer receiveServiceMsg;
    private Integer receiveSystemMsg;
    private Integer notifySound;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

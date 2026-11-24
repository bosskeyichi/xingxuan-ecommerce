package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话隐藏：用户从会话列表删除会话时记录隐藏点（该会话内见过的最大消息 id）。
 * 只是不再显示在列表，消息本体不删除；对方再来新消息（id 大于隐藏点）时会话自动恢复显示。
 */
@Data
@TableName("conversation_hide")
public class ConversationHide implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String conversationKey;

    private Long lastMessageId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

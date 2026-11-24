package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 好友关系：申请-同意制。
 * 申请时只写入一行 (申请人 → 目标, status=0)；对方同意后该行更新为 1，
 * 并补写反向行 (目标 → 申请人, status=1)。任意方向的查询都只需 where user_id = 当前用户。
 */
@Data
@TableName("friend")
public class Friend implements Serializable {

    /** 0 申请中（等待对方处理） / 1 已同意 */
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACCEPTED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long friendId;

    /** 0 申请中 / 1 已同意 */
    private Integer status;

    /** 好友备注（可选） */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

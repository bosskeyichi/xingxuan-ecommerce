package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 举报：用户举报商品或店铺。
 * 商家（店铺管理员）只能看到并处理归属本店的举报，平台管理员可处理全部；
 * 处理完成后会给举报人发一条系统消息作为回馈。
 */
@Data
@TableName("report")
public class Report implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    /** PRODUCT / SHOP */
    private String targetType;

    private Long targetId;

    /** 被举报对象所属店铺，用来商家侧数据隔离 */
    private Long shopId;

    /** 假冒伪劣 / 虚假宣传 / 违规内容 / 侵权 / 其他 */
    private String type;

    private String content;

    /** PENDING / ACCEPTED / REJECTED */
    private String status;

    private String handleNote;

    private Long handlerId;

    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

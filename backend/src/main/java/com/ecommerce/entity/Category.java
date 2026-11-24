package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("category")
public class Category implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String icon;
    private Integer sortOrder;
    private Long parentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
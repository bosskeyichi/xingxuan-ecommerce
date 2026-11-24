package com.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "user_table")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 加密 */
    private String password;

    private String nickname;
    private String email;
    private String phone;
    private String avatar;

    /** USER / ADMIN */
    private String role;

    /** 1 启用 / 0 禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 该账号经营的店铺（后台用户列表用来标记「商家」），不入库 */
    @TableField(exist = false)
    private Long shopId;

    @TableField(exist = false)
    private String shopName;
}
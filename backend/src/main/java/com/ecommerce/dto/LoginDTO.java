package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32 位")
    private String password;

    /**
     * 登录端：ADMIN 表示后台，其余按普通用户端处理。
     * 两端各自维护一个会话，别让管理员在后台操作时被前台登录挤下线。
     */
    private String scope;
}
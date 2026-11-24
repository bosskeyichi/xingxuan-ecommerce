package com.ecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileDTO {

    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^(1\\d{10})?$", message = "手机号格式不正确")
    private String phone;

    private String avatar;
}

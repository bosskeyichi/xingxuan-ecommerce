package com.ecommerce.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendAddDTO {

    @NotNull(message = "请选择要添加的用户")
    private Long userId;

    @Size(max = 50, message = "备注不能超过 50 个字符")
    private String remark;
}

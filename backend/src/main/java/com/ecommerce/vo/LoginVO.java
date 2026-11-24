package com.ecommerce.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    /** 商家账号所属店铺（用户 / 平台管理员为 null） */
    private Long shopId;
    private String shopName;
    private String token;
}
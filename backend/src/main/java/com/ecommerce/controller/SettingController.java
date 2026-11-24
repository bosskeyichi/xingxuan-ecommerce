package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.SettingDTO;
import com.ecommerce.entity.UserSetting;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.SettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个人设置：隐私与消息开关。用户端与管理端共用。
 */
@RestController
@RequestMapping("/setting")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public Result<UserSetting> get() {
        return Result.success(settingService.get(requireLogin()));
    }

    @PutMapping
    public Result<Void> save(@RequestBody @Valid SettingDTO dto) {
        settingService.save(requireLogin(), dto);
        return Result.success("设置已保存", null);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new BusinessException(401, "请先登录");
        return uid;
    }
}

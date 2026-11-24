package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.Result;
import com.ecommerce.dto.ChangePasswordDTO;
import com.ecommerce.dto.LoginDTO;
import com.ecommerce.dto.ProfileDTO;
import com.ecommerce.dto.RegisterDTO;
import com.ecommerce.entity.User;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.UserService;
import com.ecommerce.utils.SessionStore;
import com.ecommerce.vo.LoginVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final UserService userService;
    private final SessionStore sessionStore;

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    /** 注册 */
    @PostMapping("/register")
    public Result<User> register(@RequestBody @Valid RegisterDTO dto) {
        return Result.success("注册成功", userService.register(dto));
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public Result<User> me() {
        return Result.success(userService.getCurrent());
    }

    /** 更新个人资料 */
    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestBody @Valid ProfileDTO dto) {
        Long uid = requireLogin();
        return Result.success("资料已更新", userService.updateProfile(uid, dto));
    }

    /** 修改密码（修改后需重新登录） */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto) {
        Long uid = requireLogin();
        userService.changePassword(uid, dto);
        return Result.success("密码已修改，请重新登录", null);
    }

    /** 退出登录：只清理当前端的会话，另一端的登录态保留 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid != null) {
            sessionStore.remove(SessionStore.key(uid, LoginInterceptor.SCOPE.get()));
        }
        return Result.success("退出成功", null);
    }

    // ---- 后台（AdminInterceptor 只有管理员能进） ----

    @GetMapping("/admin/users")
    public Result<List<User>> adminList() {
        return Result.success(userService.listAll());
    }

    @GetMapping("/admin/users/page")
    public Result<IPage<User>> adminPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size
    ) {
        return Result.success(userService.page(current, size, keyword));
    }

    @PutMapping("/admin/users/{id}/status")
    public Result<Void> adminStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> body) {
        Integer status = body == null ? null : body.get("status");
        if (status == null) throw new com.ecommerce.exception.BusinessException("状态不能为空");
        userService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    @DeleteMapping("/admin/users/{id}")
    public Result<Void> adminDelete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }

    /** 重置用户数据：清空业务/店铺内容，账号资料与密码恢复默认 */
    @PostMapping("/admin/users/{id}/reset")
    public Result<Void> adminReset(@PathVariable Long id) {
        userService.resetData(id);
        // 不在响应里回显明文密码，默认密码见初始化文档
        return Result.success("该用户数据已重置，密码已恢复为默认密码", null);
    }

    /** 调整角色：普通用户 ↔ 普通管理员（商家） */
    @PutMapping("/admin/users/{id}/role")
    public Result<Void> adminRole(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        userService.changeRole(id, body == null ? null : body.get("role"));
        return Result.success("角色已更新", null);
    }

    /** 超级管理员主动给指定用户发送系统通知（强制送达，不受接收方开关限制） */
    @PostMapping("/admin/users/{id}/notify")
    public Result<Void> adminNotify(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        String content = body == null ? null : body.get("content");
        userService.notifyUser(id, content);
        return Result.success("系统通知已发送", null);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new com.ecommerce.exception.BusinessException(401, "未登录");
        return uid;
    }
}

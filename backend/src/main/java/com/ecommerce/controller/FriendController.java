package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.Result;
import com.ecommerce.dto.FriendAddDTO;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.FriendService;
import com.ecommerce.vo.UserCardVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友：用户端用户在普通用户之间搜索互加，管理端商家在商家之间互加；
 * 超级管理员默认拥有全部商家好友。
 */
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
@Validated
public class FriendController {

    private final FriendService friendService;

    /** 搜索用户（昵称 / 用户名 / ID），自动按当前身份限定范围 */
    @GetMapping("/search")
    public Result<IPage<UserCardVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) long size) {
        return Result.success(friendService.search(requireLogin(), keyword, filter, current, size));
    }

    @GetMapping("/list")
    public Result<List<UserCardVO>> list() {
        return Result.success(friendService.listFriends(requireLogin()));
    }

    /** 单个用户主页资料（点击好友头像 / 搜索结果进入） */
    @GetMapping("/card/{id}")
    public Result<UserCardVO> card(@PathVariable Long id) {
        return Result.success(friendService.card(requireLogin(), id));
    }

    /** 发送好友申请（对方同意后成为好友） */
    @PostMapping
    public Result<Void> add(@RequestBody @Valid FriendAddDTO dto) {
        friendService.add(requireLogin(), dto);
        return Result.success("好友申请已发送，等待对方同意", null);
    }

    /** 收到的好友申请 */
    @GetMapping("/requests")
    public Result<List<UserCardVO>> requests() {
        return Result.success(friendService.requests(requireLogin()));
    }

    /** 同意好友申请（requesterId = 申请人 id） */
    @PostMapping("/{requesterId}/accept")
    public Result<Void> accept(@PathVariable Long requesterId) {
        friendService.accept(requireLogin(), requesterId);
        return Result.success("已同意好友申请", null);
    }

    /** 拒绝好友申请 */
    @PostMapping("/{requesterId}/reject")
    public Result<Void> reject(@PathVariable Long requesterId) {
        friendService.reject(requireLogin(), requesterId);
        return Result.success("已拒绝好友申请", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        friendService.remove(requireLogin(), id);
        return Result.success("已删除好友", null);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new BusinessException(401, "请先登录");
        return uid;
    }
}

package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.BrowseRecordDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.BrowseHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/browse")
@RequiredArgsConstructor
public class BrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    /** 记录浏览足迹（需登录） */
    @PostMapping("/record")
    public Result<Void> record(@RequestBody @Valid BrowseRecordDTO dto) {
        browseHistoryService.record(requireLogin(), dto.getProductId());
        return Result.success("已记录", null);
    }

    /** 当前用户最近浏览（需登录） */
    @GetMapping("/list")
    public Result<List<Product>> list() {
        return Result.success(browseHistoryService.listRecent(requireLogin()));
    }

    /** 移除单条足迹（需登录） */
    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        browseHistoryService.remove(requireLogin(), productId);
        return Result.success("已移除", null);
    }

    /** 清空全部足迹（需登录） */
    @DeleteMapping("/clear")
    public Result<Void> clear() {
        browseHistoryService.clear(requireLogin());
        return Result.success("足迹已清空", null);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new BusinessException(401, "未登录");
        return uid;
    }
}

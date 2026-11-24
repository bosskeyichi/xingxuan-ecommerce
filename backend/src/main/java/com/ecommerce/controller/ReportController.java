package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.Result;
import com.ecommerce.dto.ReportDTO;
import com.ecommerce.dto.ReportHandleDTO;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.ReportService;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.ReportVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 举报：用户举报商品 / 店铺；商家处理本店举报，平台管理员处理全部。
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;
    private final ShopService shopService;

    @PostMapping
    public Result<Void> submit(@RequestBody @Valid ReportDTO dto) {
        reportService.submit(requireLogin(), dto);
        return Result.success("举报已提交，处理结果会通过系统消息通知你", null);
    }

    /** 我的举报 */
    @GetMapping("/mine")
    public Result<IPage<ReportVO>> mine(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size) {
        return Result.success(reportService.mine(requireLogin(), current, size));
    }

    /** 后台举报列表：商家自动只看本店，平台管理员看全部 */
    @GetMapping("/admin/page")
    public Result<IPage<ReportVO>> adminPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size) {
        return Result.success(reportService.adminPage(shopService.currentShopId(), status, keyword, current, size));
    }

    /** 待处理举报数（侧边栏角标） */
    @GetMapping("/admin/pending")
    public Result<Long> pending() {
        return Result.success(reportService.pendingCount(shopService.currentShopId()));
    }

    @PostMapping("/admin/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody @Valid ReportHandleDTO dto) {
        reportService.handle(requireLogin(), id, dto, shopService.currentShopId());
        return Result.success("已处理，并已通知举报人", null);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new BusinessException(401, "请先登录");
        return uid;
    }
}

package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.Result;
import com.ecommerce.dto.ReviewAppendDTO;
import com.ecommerce.dto.ReviewBatchDeleteDTO;
import com.ecommerce.dto.ReviewDTO;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.ReviewService;
import com.ecommerce.vo.ReviewVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    /** 商品评价列表（公开） */
    @GetMapping("/list/{productId}")
    public Result<List<ReviewVO>> list(@PathVariable Long productId) {
        return Result.success(reviewService.listByProduct(productId));
    }

    /** 评分汇总（公开） */
    @GetMapping("/summary/{productId}")
    public Result<Map<String, Object>> summary(@PathVariable Long productId) {
        return Result.success(reviewService.ratingSummary(productId));
    }

    /** 发表评价（需登录且已购买） */
    @PostMapping
    public Result<Void> add(@RequestBody @Valid ReviewDTO dto) {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new com.ecommerce.exception.BusinessException(401, "请先登录");
        reviewService.add(uid, dto);
        return Result.success("评价已发布", null);
    }

    /** 当前用户在指定订单内已发表的评价 */
    @GetMapping("/mine/order/{orderNo}")
    public Result<List<ReviewVO>> mineByOrder(@PathVariable String orderNo) {
        Long uid = requireLogin();
        return Result.success(reviewService.listMineByOrder(uid, orderNo));
    }

    /** 修改自己的评价 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ReviewDTO dto) {
        Long uid = requireLogin();
        reviewService.update(uid, id, dto);
        return Result.success("评价已更新", null);
    }

    /** 追加评价 */
    @PostMapping("/{id}/append")
    public Result<Void> append(@PathVariable Long id, @RequestBody @Valid ReviewAppendDTO dto) {
        Long uid = requireLogin();
        reviewService.append(uid, id, dto.getContent());
        return Result.success("追评已发布", null);
    }

    /** 删除评价 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long uid = requireLogin();
        reviewService.delete(uid, id);
        return Result.success("评价已删除", null);
    }

    /** 我的评价：分页（含商品名 / 封面，支持分享） */
    @GetMapping("/mine/page")
    public Result<IPage<ReviewVO>> minePage(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long size) {
        return Result.success(reviewService.minePage(requireLogin(), current, size));
    }

    /** 批量删除我的评价 */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody @Valid ReviewBatchDeleteDTO dto) {
        reviewService.batchDelete(requireLogin(), dto.getIds());
        return Result.success("评价已删除", null);
    }

    // ---- 后台（AdminInterceptor 只有管理员能进） ----

    @GetMapping("/admin/list")
    public Result<IPage<ReviewVO>> adminList(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size
    ) {
        return Result.success(reviewService.adminPage(productId, current, size));
    }

    @DeleteMapping("/admin/{id}")
    public Result<Void> adminDelete(@PathVariable Long id) {
        reviewService.adminDelete(id);
        return Result.success("评价已删除", null);
    }

    @PostMapping("/admin/{id}/hide")
    public Result<Void> adminHide(@PathVariable Long id) {
        reviewService.setHidden(id, true);
        return Result.success("评价已屏蔽", null);
    }

    @PostMapping("/admin/{id}/unhide")
    public Result<Void> adminUnhide(@PathVariable Long id) {
        reviewService.setHidden(id, false);
        return Result.success("已取消屏蔽", null);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new com.ecommerce.exception.BusinessException(401, "请先登录");
        return uid;
    }
}

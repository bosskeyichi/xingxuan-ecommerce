package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.service.ShopService;
import com.ecommerce.service.StatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台仪表盘统计：商家只看本店数据；平台管理员默认看全平台，可传 shopId 下钻到单个店铺
 * （AdminInterceptor 保证已登录且具备后台权限）。
 */
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
@Validated
public class StatsController {

    private final StatsService statsService;
    private final ShopService shopService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Long shopId) {
        return Result.success(statsService.overview(resolveShopId(shopId)));
    }

    /** 近 N 天销售趋势 */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int days,
            @RequestParam(required = false) Long shopId) {
        return Result.success(statsService.salesTrend(days, resolveShopId(shopId)));
    }

    /** 库存预警 */
    @GetMapping("/low-stock")
    public Result<List<Map<String, Object>>> lowStock(
            @RequestParam(defaultValue = "10") @Min(0) @Max(1000) int threshold,
            @RequestParam(required = false) Long shopId) {
        return Result.success(statsService.lowStock(threshold, resolveShopId(shopId)));
    }

    /** 热销商品 */
    @GetMapping("/top-products")
    public Result<List<Map<String, Object>>> topProducts(
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit,
            @RequestParam(required = false) Long shopId) {
        return Result.success(statsService.topProducts(limit, resolveShopId(shopId)));
    }

    /**
     * 统计范围：商家强制本店（忽略请求里的 shopId，防止越权查看别家数据）；
     * 平台管理员（currentShopId = null）才允许按 shopId 下钻。
     */
    private Long resolveShopId(Long requested) {
        Long own = shopService.currentShopId();
        return own != null ? own : requested;
    }
}

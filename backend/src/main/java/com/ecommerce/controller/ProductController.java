package com.ecommerce.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.Result;
import com.ecommerce.dto.ProductDTO;
import com.ecommerce.entity.Product;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.ShopService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;
    private final ShopService shopService;

    /**
     * 商品分页（可选分类/关键词/价格区间/排序/店铺）。
     * 前台只返回上架商品：这里不接受 status 参数，别让匿名用户通过 status=0 枚举下架商品；
     * 需要查看全部状态请使用后台 /product/admin/list。
     */
    @GetMapping("/list")
    public Result<Page<Product>> list(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long shopId
    ) {
        return Result.success(productService.page(current, size, categoryId, keyword, 1,
                minPrice, maxPrice, inStock, sort, shopId));
    }

    /** 关键词搜索 */
    @GetMapping("/search")
    public Result<Page<Product>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) long size,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String sort
    ) {
        return Result.success(productService.page(current, size, null, keyword, 1,
                minPrice, maxPrice, inStock, sort, null));
    }

    /** 猜你喜欢：按浏览过的分类聚合推荐 */
    @GetMapping("/recommend")
    public Result<List<Product>> recommend(
            @RequestParam(required = false) String categoryIds,
            @RequestParam(defaultValue = "4") @Min(1) @Max(12) int limit
    ) {
        return Result.success(productService.recommend(categoryIds, limit));
    }

    /** 商品详情 */
    @GetMapping("/detail/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        return Result.success(productService.detail(id));
    }

    /** 热销商品（首页推荐位） */
    @GetMapping("/hot")
    public Result<List<Product>> hot(@RequestParam(defaultValue = "8") @Min(1) @Max(50) int limit) {
        return Result.success(productService.hot(limit));
    }

    // ---- 后台（AdminInterceptor 只有管理员能进） ----

    /** 后台商品分页：可查看全部状态 */
    @GetMapping("/admin/list")
    public Result<Page<Product>> adminList(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long shopId
    ) {
        // 商家固定看自己店铺；平台管理员可用 shopId 参数按店铺筛查
        Long scope = shopService.currentShopId();
        return Result.success(productService.adminPage(current, size, categoryId, keyword, status,
                scope != null ? scope : shopId));
    }

    @PostMapping("/admin")
    public Result<Void> create(@RequestBody @Valid ProductDTO dto) {
        productService.save(dto, shopService.currentShopId());
        return Result.success("新增成功", null);
    }

    @PutMapping("/admin/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ProductDTO dto) {
        productService.update(id, dto, shopService.currentShopId());
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id, shopService.currentShopId());
        return Result.success("删除成功", null);
    }
}

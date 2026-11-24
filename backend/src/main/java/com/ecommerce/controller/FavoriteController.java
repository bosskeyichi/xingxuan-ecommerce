package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.entity.Product;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 我的收藏商品列表 */
    @GetMapping("/list")
    public Result<List<Product>> list() {
        Long uid = requireLogin();
        return Result.success(favoriteService.list(uid));
    }

    /** 已收藏商品 ID */
    @GetMapping("/ids")
    public Result<List<Long>> ids() {
        Long uid = requireLogin();
        return Result.success(favoriteService.ids(uid));
    }

    @PostMapping("/{productId}")
    public Result<Void> add(@PathVariable Long productId) {
        Long uid = requireLogin();
        favoriteService.add(uid, productId);
        return Result.success("已收藏", null);
    }

    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        Long uid = requireLogin();
        favoriteService.remove(uid, productId);
        return Result.success("已取消收藏", null);
    }

    /** 查询某商品是否已收藏 */
    @GetMapping("/check/{productId}")
    public Result<Map<String, Object>> check(@PathVariable Long productId) {
        Long uid = LoginInterceptor.USER_ID.get();
        Map<String, Object> m = new HashMap<>();
        m.put("favorited", uid != null && favoriteService.isFavorited(uid, productId));
        return Result.success(m);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new com.ecommerce.exception.BusinessException(401, "请先登录");
        return uid;
    }
}

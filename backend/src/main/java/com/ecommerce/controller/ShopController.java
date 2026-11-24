package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.ShopDTO;
import com.ecommerce.entity.Shop;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.ShopVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 店铺：前台店铺列表 / 详情，后台商家维护自己的店铺，平台管理员管理全部店铺。
 */
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    /** 营业中的店铺（前台选店用） */
    @GetMapping("/list")
    public Result<List<Shop>> list() {
        return Result.success(shopService.listActive());
    }

    /** 按名称 / 简介搜索店铺（前台店铺搜索框） */
    @GetMapping("/search")
    public Result<List<Shop>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(shopService.search(keyword, limit));
    }

    /** 店铺详情 */
    @GetMapping("/detail/{id}")
    public Result<Shop> detail(@PathVariable Long id) {
        return Result.success(shopService.detail(id));
    }

    /** 商家：自己的店铺信息（平台管理员返回 null） */
    @GetMapping("/admin/mine")
    public Result<Shop> mine() {
        return Result.success(shopService.mine());
    }

    /** 商家：修改自己的店铺资料 */
    @PutMapping("/admin/mine")
    public Result<Void> updateMine(@RequestBody @Valid ShopDTO dto) {
        shopService.updateMine(dto);
        return Result.success("店铺资料已更新", null);
    }

    /** 平台管理员：全部店铺 + 经营数据（平台级接口只放 ADMIN 进来） */
    @GetMapping("/admin/list")
    public Result<List<ShopVO>> adminList() {
        return Result.success(shopService.adminList());
    }

    /** 平台管理员：开店 / 关店 */
    @PutMapping("/admin/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        shopService.updateStatus(id, body == null ? null : body.get("status"));
        return Result.success("店铺状态已更新", null);
    }

    /** 当前登录身份：用来后台前端判断是平台管理员还是商家 */
    @GetMapping("/admin/context")
    public Result<Map<String, Object>> context() {
        Shop shop = shopService.mine();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("role", LoginInterceptor.ROLE.get());
        data.put("shopId", shop == null ? null : shop.getId());
        data.put("shopName", shop == null ? null : shop.getName());
        data.put("platformAdmin", "ADMIN".equals(LoginInterceptor.ROLE.get()));
        return Result.success(data);
    }
}

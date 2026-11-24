package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.Result;
import com.ecommerce.dto.CouponDTO;
import com.ecommerce.entity.Coupon;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.CouponService;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.UserCouponVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final ShopService shopService;

    /** 可领取的优惠券（公开） */
    @GetMapping("/available")
    public Result<List<Coupon>> available() {
        return Result.success(couponService.listAvailable());
    }

    /** 当前用户可用的优惠券（按金额过滤） */
    @GetMapping("/usable")
    public Result<List<UserCouponVO>> usable(@RequestParam(required = false, defaultValue = "0") BigDecimal amount) {
        Long uid = LoginInterceptor.USER_ID.get();
        return Result.success(couponService.listUsable(uid, amount));
    }

    /** 我的优惠券 */
    @GetMapping("/mine")
    public Result<List<UserCouponVO>> mine() {
        Long uid = LoginInterceptor.USER_ID.get();
        return Result.success(couponService.listMine(uid));
    }

    /** 领取优惠券 */
    @PostMapping("/claim/{id}")
    public Result<Void> claim(@PathVariable Long id) {
        Long uid = LoginInterceptor.USER_ID.get();
        couponService.claim(uid, id);
        return Result.success("领取成功", null);
    }

    // ---- 后台管理（AdminInterceptor 只有管理员能进） ----

    @GetMapping("/admin/list")
    public Result<IPage<Coupon>> adminList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        // 商家只看本店券；平台管理员看全部（含全平台券与各店券）
        return Result.success(couponService.adminPage((int) current, (int) size, keyword, shopService.currentShopId()));
    }

    @PostMapping("/admin")
    public Result<Void> create(@RequestBody @Valid CouponDTO dto) {
        couponService.save(dto, shopService.currentShopId());
        return Result.success("已创建", null);
    }

    @PutMapping("/admin/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CouponDTO dto) {
        couponService.update(id, dto, shopService.currentShopId());
        return Result.success("已更新", null);
    }

    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        couponService.delete(id, shopService.currentShopId());
        return Result.success("已删除", null);
    }
}

package com.ecommerce.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.common.Result;
import com.ecommerce.dto.OrderBatchDeleteDTO;
import com.ecommerce.dto.OrderBatchPayDTO;
import com.ecommerce.dto.OrderSubmitDTO;
import com.ecommerce.dto.ShipDTO;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.OrderService;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.OrderVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;
    private final ShopService shopService;

    /** 提交订单：购物车按店铺拆分，跨店时一次生成多笔订单 */
    @PostMapping("/submit")
    public Result<List<String>> submit(@RequestBody @Valid OrderSubmitDTO dto) {
        Long uid = LoginInterceptor.USER_ID.get();
        List<String> orderNos = orderService.submit(uid, dto);
        String msg = orderNos.size() > 1
                ? "已按店铺生成 " + orderNos.size() + " 笔订单"
                : "下单成功";
        return Result.success(msg, orderNos);
    }

    /** 订单列表 */
    @GetMapping("/list")
    public Result<IPage<OrderVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size
    ) {
        Long uid = LoginInterceptor.USER_ID.get();
        return Result.success(orderService.page(uid, status, current, size));
    }

    /** 订单详情 */
    @GetMapping("/detail/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        Long uid = LoginInterceptor.USER_ID.get();
        return Result.success(orderService.detail(uid, orderNo));
    }

    /** 模拟支付 */
    @PostMapping("/pay/{orderNo}")
    public Result<Void> pay(@PathVariable String orderNo) {
        Long uid = LoginInterceptor.USER_ID.get();
        orderService.mockPay(uid, orderNo);
        return Result.success("支付成功", null);
    }

    /** 合并支付：跨店结算拆出的多笔订单一次付清（订单仍按店铺分开，便于各自发货） */
    @PostMapping("/pay/batch")
    public Result<Integer> payBatch(@RequestBody @Valid OrderBatchPayDTO dto) {
        Long uid = LoginInterceptor.USER_ID.get();
        int count = orderService.mockPayBatch(uid, dto.getOrderNos());
        return Result.success("已支付 " + count + " 笔订单", count);
    }

    /** 取消订单 */
    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancel(@PathVariable String orderNo) {
        Long uid = LoginInterceptor.USER_ID.get();
        orderService.cancel(uid, orderNo);
        return Result.success("订单已取消", null);
    }

    /** 确认收货 */
    @PostMapping("/confirm/{orderNo}")
    public Result<Void> confirm(@PathVariable String orderNo) {
        Long uid = LoginInterceptor.USER_ID.get();
        orderService.confirmReceipt(uid, orderNo);
        return Result.success("已确认收货", null);
    }

    /** 删除单笔历史订单（仅已完成 / 已取消） */
    @DeleteMapping("/{orderNo}")
    public Result<Void> delete(@PathVariable String orderNo) {
        Long uid = LoginInterceptor.USER_ID.get();
        orderService.delete(uid, orderNo);
        return Result.success("订单已删除", null);
    }

    /** 批量删除历史订单（仅已完成 / 已取消） */
    @PostMapping("/batch-delete")
    public Result<Integer> batchDelete(@RequestBody @Valid OrderBatchDeleteDTO dto) {
        Long uid = LoginInterceptor.USER_ID.get();
        int count = orderService.batchDelete(uid, dto.getOrderNos());
        return Result.success("已删除 " + count + " 笔订单", count);
    }

    // ---- 后台（AdminInterceptor 只有管理员能进） ----

    @GetMapping("/admin/list")
    public Result<IPage<OrderVO>> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long shopId
    ) {
        // 商家固定看自己店铺；平台管理员可按店铺筛查
        Long scope = shopService.currentShopId();
        return Result.success(orderService.adminPage(status, current, size, scope != null ? scope : shopId));
    }

    @PostMapping("/admin/ship/{orderNo}")
    public Result<Void> ship(@PathVariable String orderNo, @RequestBody ShipDTO dto) {
        // 只能操作自己店铺的订单：商家=本店，超管=自己名下的店（别家店订单只读）
        orderService.ship(orderNo, shopService.requireOperableShopId(), dto);
        return Result.success("已发货", null);
    }

    @PostMapping("/admin/complete/{orderNo}")
    public Result<Void> complete(@PathVariable String orderNo) {
        orderService.complete(orderNo, shopService.requireOperableShopId());
        return Result.success("已完成", null);
    }

    /** 后台：删除单笔历史订单（只能删自己店铺的） */
    @DeleteMapping("/admin/{orderNo}")
    public Result<Void> adminDelete(@PathVariable String orderNo) {
        orderService.adminDelete(orderNo, shopService.requireOperableShopId());
        return Result.success("订单已删除", null);
    }

    /** 后台：批量删除历史订单（只能删自己店铺的） */
    @PostMapping("/admin/batch-delete")
    public Result<Integer> adminBatchDelete(@RequestBody @Valid OrderBatchDeleteDTO dto) {
        int count = orderService.adminBatchDelete(dto.getOrderNos(), shopService.requireOperableShopId());
        return Result.success("已删除 " + count + " 笔订单", count);
    }

    /** 导出订单 CSV（商家仅导出本店订单） */
    @GetMapping("/admin/export")
    public void export(HttpServletResponse response) throws IOException {
        orderService.exportCsv(response, shopService.currentShopId());
    }
}

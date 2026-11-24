package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.CartAddDTO;
import com.ecommerce.dto.CartQuantityDTO;
import com.ecommerce.dto.CartSelectedDTO;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.CartService;
import com.ecommerce.vo.CartVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/list")
    public Result<List<CartVO>> list() {
        Long uid = LoginInterceptor.USER_ID.get();
        return Result.success(cartService.list(uid));
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody @Valid CartAddDTO dto) {
        Long uid = LoginInterceptor.USER_ID.get();
        cartService.add(uid, dto.getProductId(), dto.getQuantity() == null ? 1 : dto.getQuantity());
        return Result.success();
    }

    @PutMapping("/quantity")
    public Result<Void> updateQty(@RequestBody @Valid CartQuantityDTO dto) {
        Long uid = LoginInterceptor.USER_ID.get();
        cartService.updateQuantity(uid, dto.getCartId(), dto.getQuantity());
        return Result.success();
    }

    @PutMapping("/selected")
    public Result<Void> updateSelected(@RequestBody @Valid CartSelectedDTO dto) {
        Long uid = LoginInterceptor.USER_ID.get();
        cartService.updateSelected(uid, dto.getCartId(), dto.getSelected());
        return Result.success();
    }

    @DeleteMapping("/{cartId}")
    public Result<Void> delete(@PathVariable Long cartId) {
        Long uid = LoginInterceptor.USER_ID.get();
        cartService.delete(uid, cartId);
        return Result.success();
    }
}

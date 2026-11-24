package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.AddressDTO;
import com.ecommerce.entity.Address;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收货地址簿（需登录）
 */
@RestController
@RequestMapping("/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    public Result<List<Address>> list() {
        return Result.success(addressService.list(requireLogin()));
    }

    @PostMapping
    public Result<Address> add(@RequestBody @Valid AddressDTO dto) {
        return Result.success("添加成功", addressService.add(requireLogin(), dto));
    }

    @PutMapping("/{id}")
    public Result<Address> update(@PathVariable Long id, @RequestBody @Valid AddressDTO dto) {
        return Result.success("修改成功", addressService.update(requireLogin(), id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        addressService.remove(requireLogin(), id);
        return Result.success("删除成功", null);
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(requireLogin(), id);
        return Result.success("已设为默认地址", null);
    }

    private Long requireLogin() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new BusinessException(401, "请先登录");
        return uid;
    }
}

package com.ecommerce.controller;

import com.ecommerce.common.Result;
import com.ecommerce.dto.CategoryDTO;
import com.ecommerce.entity.Category;
import com.ecommerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final ProductService productService;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(productService.categoryList());
    }

    @PostMapping("/admin")
    public Result<Long> create(@RequestBody @Valid CategoryDTO dto) {
        return Result.success("新增成功", productService.createCategory(dto));
    }

    @PutMapping("/admin/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CategoryDTO dto) {
        productService.updateCategory(id, dto);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.deleteCategory(id);
        return Result.success("删除成功", null);
    }
}

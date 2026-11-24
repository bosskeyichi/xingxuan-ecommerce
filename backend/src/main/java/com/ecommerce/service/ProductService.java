package com.ecommerce.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.CategoryDTO;
import com.ecommerce.dto.ProductDTO;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    /** 前台商品列表：默认只返回上架商品，支持价格区间、仅看有货、排序与按店铺筛选 */
    Page<Product> page(long current, long size, Long categoryId, String keyword, Integer status,
                       BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, String sort, Long shopId);

    /** 后台商品列表：商家传入自己的店铺 ID，平台管理员传 null 看全部 */
    Page<Product> adminPage(long current, long size, Long categoryId, String keyword, Integer status, Long shopId);

    /** 猜你喜欢：按浏览过的分类聚合推荐热销商品 */
    List<Product> recommend(String categoryIds, int limit);

    Product detail(Long id);

    void save(ProductDTO dto, Long operatorShopId);

    void update(Long id, ProductDTO dto, Long operatorShopId);

    void delete(Long id, Long operatorShopId);

    List<Category> categoryList();

    Long createCategory(CategoryDTO dto);

    void updateCategory(Long id, CategoryDTO dto);

    void deleteCategory(Long id);

    /** 热销商品（按销量倒序） */
    List<Product> hot(int limit);

    /** 库存预警商品 */
    List<Product> lowStock(int threshold);
}

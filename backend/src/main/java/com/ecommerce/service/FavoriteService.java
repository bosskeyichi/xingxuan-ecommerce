package com.ecommerce.service;

import com.ecommerce.entity.Product;

import java.util.List;

public interface FavoriteService {

    /** 我的收藏（商品列表） */
    List<Product> list(Long userId);

    /** 已收藏的商品 ID 集合 */
    List<Long> ids(Long userId);

    void add(Long userId, Long productId);

    void remove(Long userId, Long productId);

    /** 是否已收藏 */
    boolean isFavorited(Long userId, Long productId);
}

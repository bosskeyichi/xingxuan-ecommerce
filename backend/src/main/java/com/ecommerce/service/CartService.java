package com.ecommerce.service;

import com.ecommerce.vo.CartVO;
import java.util.List;

public interface CartService {

    List<CartVO> list(Long userId);

    void add(Long userId, Long productId, Integer quantity);

    void updateQuantity(Long userId, Long cartId, Integer quantity);

    void updateSelected(Long userId, Long cartId, Integer selected);

    void delete(Long userId, Long cartId);

    /** 批量结算：返回选中的购物车项（同时清空购物车） */
    List<CartVO> checkout(Long userId);
}
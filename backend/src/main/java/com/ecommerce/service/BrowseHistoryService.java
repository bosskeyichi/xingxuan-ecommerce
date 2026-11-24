package com.ecommerce.service;

import com.ecommerce.entity.Product;

import java.util.List;

public interface BrowseHistoryService {

    /** 记录一次浏览（去重、最多保留最近 12 条） */
    void record(Long userId, Long productId);

    /** 最近浏览的商品（按浏览时间倒序，最多 12 条，已下架商品自动过滤） */
    List<Product> listRecent(Long userId);

    /** 移除某条足迹 */
    void remove(Long userId, Long productId);

    /** 清空当前用户全部足迹 */
    void clear(Long userId);
}

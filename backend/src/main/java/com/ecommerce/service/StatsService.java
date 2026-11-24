package com.ecommerce.service;

import java.util.List;
import java.util.Map;

public interface StatsService {

    /** 仪表盘总览：shopId 为 null 时统计全平台，否则只统计该店铺 */
    Map<String, Object> overview(Long shopId);

    /** 近 N 天销售趋势（按支付时间统计，含订单数与金额） */
    List<Map<String, Object>> salesTrend(int days, Long shopId);

    /** 库存预警商品 */
    List<Map<String, Object>> lowStock(int threshold, Long shopId);

    /** 热销商品 TOP N */
    List<Map<String, Object>> topProducts(int limit, Long shopId);
}

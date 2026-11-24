package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.dto.ReviewDTO;
import com.ecommerce.vo.ReviewVO;

import java.util.List;
import java.util.Map;

public interface ReviewService {

    /** 商品评价列表（过滤被屏蔽的评价） */
    List<ReviewVO> listByProduct(Long productId);

    /** 当前用户在指定订单内已发表的评价（按商品匹配） */
    List<ReviewVO> listMineByOrder(Long userId, String orderNo);

    /** 我的评价：分页（含商品名、图片，便于分享） */
    IPage<ReviewVO> minePage(Long userId, long current, long size);

    /** 批量删除自己的评价 */
    void batchDelete(Long userId, List<Long> ids);

    /** 发表评价（需已购买该商品） */
    void add(Long userId, ReviewDTO dto);

    /** 修改自己的评价 */
    void update(Long userId, Long reviewId, ReviewDTO dto);

    /** 追加评价 */
    void append(Long userId, Long reviewId, String content);

    /** 删除自己的评价（管理员也可调用） */
    void delete(Long userId, Long reviewId);

    /** 商品评分汇总：avgRating(平均值) / count(条数) / 各星级分布 */
    Map<String, Object> ratingSummary(Long productId);

    // ---- 后台 ----

    /** 后台：分页查看某商品的评价（含已屏蔽） */
    IPage<ReviewVO> adminPage(Long productId, long current, long size);

    /** 后台：删除评价 */
    void adminDelete(Long reviewId);

    /** 后台：屏蔽 / 取消屏蔽评价 */
    void setHidden(Long reviewId, boolean hidden);
}

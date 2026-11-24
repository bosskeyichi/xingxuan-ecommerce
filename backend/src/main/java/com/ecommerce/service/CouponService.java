package com.ecommerce.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.dto.CouponDTO;
import com.ecommerce.entity.Coupon;
import com.ecommerce.vo.UserCouponVO;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    /** 对外可领取的优惠券（上架、在有效期内、未领完） */
    List<Coupon> listAvailable();

    /** 我的优惠券（含已使用 / 未使用） */
    List<UserCouponVO> listMine(Long userId);

    /** 当前订单可用的优惠券（返回全部有效未使用券，含适用店铺，由前端按店铺筛选） */
    List<UserCouponVO> listUsable(Long userId, BigDecimal amount);

    /** 领取优惠券（一人一张，原子递增 claimed） */
    void claim(Long userId, Long couponId);

    /** 后台分页列表：商家只看本店券，平台管理员看全部 */
    IPage<Coupon> adminPage(int current, int size, String keyword, Long scopeShopId);

    /** 新建：商家强制本店，平台管理员可选全平台券或指定店铺 */
    void save(CouponDTO dto, Long scopeShopId);

    void update(Long id, CouponDTO dto, Long scopeShopId);

    void delete(Long id, Long scopeShopId);
}

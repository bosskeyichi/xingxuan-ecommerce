package com.ecommerce.service;

import com.ecommerce.dto.ShopDTO;
import com.ecommerce.entity.Shop;
import com.ecommerce.vo.ShopVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ShopService {

    /** 营业中的店铺（前台可选店铺） */
    List<Shop> listActive();

    /** 按店铺名 / 简介搜索营业中的店铺 */
    List<Shop> search(String keyword, int limit);

    Shop detail(Long id);

    /** 当前登录商家自己的店铺；平台管理员返回 null */
    Shop mine();

    /** 按店主账号查店铺（登录时回填店铺信息用） */
    Shop getByOwner(Long ownerId);

    /**
     * 当前请求的数据范围：平台管理员返回 null（不限店铺），商家返回自己的店铺 ID。
     */
    Long currentShopId();

    /** 商家视角必须能定位到店铺，否则拒绝操作 */
    Long requireShopId();

    /**
     * 店铺经营动作（发货 / 完成订单）可作用的店铺：只认「自己拥有的店铺」。
     * 商家 = 本店；平台管理员（超管）如果自己也开了店（如官方旗舰店），只能在自家店履约，
     * 别家店的订单依旧只读，不会出现「管理员替别家商家发货」。
     */
    Long requireOperableShopId();

    /** 平台管理员：全部店铺 + 经营数据 */
    List<ShopVO> adminList();

    void updateMine(ShopDTO dto);

    void updateStatus(Long id, Integer status);

    /** 批量取店铺名，供商品 / 订单 VO 回填 */
    Map<Long, String> nameMap(Collection<Long> shopIds);
}

package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.Cart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartMapper extends BaseMapper<Cart> {

    /**
     * 复用被逻辑删除的购物车行（user_id + product_id 唯一索引下，重新加购不能直接 insert）。
     *
     * @return 受影响行数，0 表示没有被逻辑删除的同款商品
     */
    @Update("UPDATE cart SET deleted = 0, quantity = #{quantity}, selected = 1, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND product_id = #{productId} AND deleted = 1")
    int restoreDeleted(@Param("userId") Long userId,
                       @Param("productId") Long productId,
                       @Param("quantity") Integer quantity);
}

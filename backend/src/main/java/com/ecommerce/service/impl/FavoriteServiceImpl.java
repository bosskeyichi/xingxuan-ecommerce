package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.entity.Favorite;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.FavoriteMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Product> list(Long userId) {
        List<Favorite> favs = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreatedAt));
        if (favs.isEmpty()) return List.of();
        List<Long> ids = favs.stream().map(Favorite::getProductId).distinct().toList();
        return productMapper.selectBatchIds(ids);
    }

    @Override
    public List<Long> ids(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId))
                .stream().map(Favorite::getProductId).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void add(Long userId, Long productId) {
        if (productMapper.selectById(productId) == null) throw new BusinessException("商品不存在");
        Long exist = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        if (exist != null && exist > 0) return;
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setProductId(productId);
        favoriteMapper.insert(f);
    }

    @Override
    @Transactional
    public void remove(Long userId, Long productId) {
        // 物理删除：收藏表有 (user_id, product_id) 唯一键，
        // 若仅做逻辑删除，残留行会导致用户重新收藏时唯一键冲突。
        jdbcTemplate.update("DELETE FROM favorite WHERE user_id = ? AND product_id = ?", userId, productId);
    }

    @Override
    public boolean isFavorited(Long userId, Long productId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId)) > 0;
    }
}

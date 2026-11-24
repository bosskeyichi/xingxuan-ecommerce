package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.entity.BrowseHistory;
import com.ecommerce.entity.Product;
import com.ecommerce.mapper.BrowseHistoryMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.BrowseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    private static final int MAX_KEEP = 12;

    private final BrowseHistoryMapper browseMapper;
    private final ProductMapper productMapper;

    @Override
    public void record(Long userId, Long productId) {
        if (userId == null || productId == null) return;
        Product p = productMapper.selectById(productId);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) return;
        // 去重：先删除同一用户同一商品的旧记录，再以最新时间重新写入
        browseMapper.delete(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getProductId, productId));
        BrowseHistory bh = new BrowseHistory();
        bh.setUserId(userId);
        bh.setProductId(productId);
        browseMapper.insert(bh);

        // 只保留最近 MAX_KEEP 条，别让单用户足迹无限增长
        List<BrowseHistory> all = browseMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .orderByDesc(BrowseHistory::getCreatedAt));
        if (all.size() > MAX_KEEP) {
            List<Long> delIds = all.stream().skip(MAX_KEEP).map(BrowseHistory::getId).collect(Collectors.toList());
            browseMapper.deleteBatchIds(delIds);
        }
    }

    @Override
    public List<Product> listRecent(Long userId) {
        if (userId == null) return List.of();
        List<BrowseHistory> hist = browseMapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .orderByDesc(BrowseHistory::getCreatedAt)
                .last("LIMIT " + MAX_KEEP));
        if (hist.isEmpty()) return List.of();

        List<Long> pids = hist.stream().map(BrowseHistory::getProductId).collect(Collectors.toList());
        List<Product> products = productMapper.selectBatchIds(pids);
        if (products == null || products.isEmpty()) return List.of();

        // 下架 / 删除商品不展示，并按浏览时间排序
        Map<Long, Product> map = products.stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1 && (p.getDeleted() == null || p.getDeleted() == 0))
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a, LinkedHashMap::new));
        return pids.stream().map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void remove(Long userId, Long productId) {
        browseMapper.delete(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getUserId, userId)
                .eq(BrowseHistory::getProductId, productId));
    }

    @Override
    public void clear(Long userId) {
        browseMapper.delete(new LambdaQueryWrapper<BrowseHistory>().eq(BrowseHistory::getUserId, userId));
    }
}

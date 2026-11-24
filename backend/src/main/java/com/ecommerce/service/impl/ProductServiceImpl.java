package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.CategoryDTO;
import com.ecommerce.dto.ProductDTO;
import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.CategoryMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.ProductService;
import com.ecommerce.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ShopService shopService;

    @Override
    public Page<Product> page(long current, long size, Long categoryId, String keyword, Integer status,
                              BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, String sort, Long shopId) {
        // 前台默认只展示上架商品，且隐藏「已关店」店铺的商品
        return doPage(current, size, categoryId, keyword, status == null ? 1 : status,
                minPrice, maxPrice, inStock, sort, shopId, true);
    }

    @Override
    public Page<Product> adminPage(long current, long size, Long categoryId, String keyword, Integer status,
                                   Long shopId) {
        // 商家只看自己店铺，平台管理员 shopId 为 null 时看全部（含已关店店铺，便于商家整理）
        return doPage(current, size, categoryId, keyword, status, null, null, null, null, shopId, false);
    }

    private Page<Product> doPage(long current, long size, Long categoryId, String keyword, Integer status,
                                 BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, String sort,
                                 Long shopId, boolean onlyOpenShop) {
        String kw = StringUtils.hasText(keyword) ? sanitizeKeyword(keyword) : "";
        LambdaQueryWrapper<Product> q = StringUtils.hasText(kw)
                ? matchByKeyword(kw, onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock)
                : baseQuery(onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock);
        applySort(q, sort);
        Page<Product> page = productMapper.selectPage(new Page<>(current, size), q);
        fillShopNames(page.getRecords());
        return page;
    }

    /**
     * 关键词匹配：四级渐进（命中即停），既保精确也保召回。
     * ① 商品名 / 搜索别名包含关键词 —— 最精确；
     * ② 去掉 1~2 个字的前缀或后缀再试 —— 覆盖「男鞋 → 鞋」「男士运动鞋 → 运动鞋」「苹果手机 → 手机」这类「修饰词 + 品类词」的中文习惯；
     * ③ 分类名命中 —— 如「手机数码」「数码」；
     * ④ 名称 / 别名 / 描述兜底 —— 如「降噪」只写在卖点里。
     */
    private LambdaQueryWrapper<Product> matchByKeyword(String kw, boolean onlyOpenShop, Long shopId, Long categoryId,
                                                      Integer status, BigDecimal minPrice, BigDecimal maxPrice,
                                                      Boolean inStock) {
        LambdaQueryWrapper<Product> q = nameOrKeywords(kw, onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock);
        if (productMapper.selectCount(q) > 0) return q;

        // ② 逐级砍掉前后缀（最多各 2 个字），按「越少砍越优先」的顺序试
        if (kw.length() > 1) {
            int maxDrop = Math.min(2, kw.length() - 1);
            for (int drop = 1; drop <= maxDrop; drop++) {
                q = nameOrKeywords(kw.substring(drop), onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock);
                if (productMapper.selectCount(q) > 0) return q;
            }
            for (int drop = 1; drop <= maxDrop; drop++) {
                q = nameOrKeywords(kw.substring(0, kw.length() - drop), onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock);
                if (productMapper.selectCount(q) > 0) return q;
            }
        }

        // ③ 分类名
        List<Long> catIds = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .select(Category::getId)
                        .like(Category::getName, kw))
                .stream().map(Category::getId).toList();
        if (!catIds.isEmpty()) {
            q = baseQuery(onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock);
            q.in(Product::getCategoryId, catIds);
            if (productMapper.selectCount(q) > 0) return q;
        }

        // ④ 描述兜底
        final String fw = kw;
        return baseQuery(onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock)
                .and(w -> w.like(Product::getName, fw)
                        .or().like(Product::getKeywords, fw)
                        .or().like(Product::getDescription, fw));
    }

    /** 商品名或搜索别名包含指定片段 */
    private LambdaQueryWrapper<Product> nameOrKeywords(String part, boolean onlyOpenShop, Long shopId, Long categoryId,
                                                       Integer status, BigDecimal minPrice, BigDecimal maxPrice,
                                                       Boolean inStock) {
        final String p = part;
        return baseQuery(onlyOpenShop, shopId, categoryId, status, minPrice, maxPrice, inStock)
                .and(w -> w.like(Product::getName, p).or().like(Product::getKeywords, p));
    }

    /** 公共筛选条件（店铺 / 分类 / 状态 / 价格 / 有货），关键词条件各层单独追加 */
    private LambdaQueryWrapper<Product> baseQuery(boolean onlyOpenShop, Long shopId, Long categoryId, Integer status,
                                                  BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<>();
        if (onlyOpenShop) {
            q.inSql(Product::getShopId, "SELECT id FROM shop WHERE status = 1 AND deleted = 0");
        }
        if (shopId != null) q.eq(Product::getShopId, shopId);
        if (categoryId != null) q.eq(Product::getCategoryId, categoryId);
        if (status != null) q.eq(Product::getStatus, status);
        // 价格区间（min > max 时自动交换，别让空结果）
        BigDecimal min = minPrice;
        BigDecimal max = maxPrice;
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal t = min;
            min = max;
            max = t;
        }
        if (min != null) q.ge(Product::getPrice, min);
        if (max != null) q.le(Product::getPrice, max);
        // 仅看有货（库存 > 0）
        if (Boolean.TRUE.equals(inStock)) q.gt(Product::getStock, 0);
        return q;
    }

    /** 排序：白名单校验，防止任意字段注入；统一追加 id 兜底，保证分页稳定 */
    private void applySort(LambdaQueryWrapper<Product> q, String sort) {
        if ("price_asc".equals(sort)) {
            q.orderByAsc(Product::getPrice);
        } else if ("price_desc".equals(sort)) {
            q.orderByDesc(Product::getPrice);
        } else if ("sales_desc".equals(sort)) {
            q.orderByDesc(Product::getSales);
        } else if ("newest".equals(sort)) {
            q.orderByDesc(Product::getCreatedAt);
        } else {
            // 综合：按上架时间倒序（默认排序）
            q.orderByDesc(Product::getCreatedAt);
        }
        q.orderByDesc(Product::getId);
    }

    @Override
    public List<Product> recommend(String categoryIds, int limit) {
        int n = Math.max(1, Math.min(limit, 12));
        List<Long> ids = new java.util.ArrayList<>();
        if (StringUtils.hasText(categoryIds)) {
            for (String s : categoryIds.split(",")) {
                try {
                    ids.add(Long.parseLong(s.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略非法分类 id
                }
                if (ids.size() >= 5) break;
            }
        }
        if (ids.isEmpty()) return hot(n);
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .inSql(Product::getShopId, "SELECT id FROM shop WHERE status = 1 AND deleted = 0")
                .in(Product::getCategoryId, ids)
                .orderByDesc(Product::getSales)
                .last("LIMIT " + n));
        fillShopNames(list);
        return list;
    }

    @Override
    public Product detail(Long id) {
        Product p = productMapper.selectById(id);
        // 下架/未上架商品对前台不可见（列表/搜索已过滤 status=1，详情接口也同样限定）
        if (p == null || (p.getStatus() != null && p.getStatus() != 1)) {
            throw new BusinessException("商品已下架或不存在");
        }
        p.setShopName(shopService.nameMap(java.util.List.of(p.getShopId())).get(p.getShopId()));
        // 关店后商品仍保留（商家可整理再开店），但前台要提示并禁止加购
        if (p.getShopId() != null) {
            com.ecommerce.entity.Shop shop = shopService.detail(p.getShopId());
            p.setShopStatus(shop == null ? 0 : shop.getStatus());
        }
        return p;
    }

    @Override
    @Transactional
    public void save(ProductDTO dto, Long operatorShopId) {
        if (dto.getCategoryId() != null && categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BusinessException("所属分类不存在");
        }
        // 商家建品自动挂到自己店铺；平台管理员也只能给「自己绑定的店铺」建品，
        // 其他店铺的商品仅可查看/筛选，不允许替别家店新建（dto.shopId 不再作为归属依据）
        Long shopId = operatorShopId;
        if (shopId == null) {
            com.ecommerce.entity.Shop own = shopService.mine();
            if (own == null) throw new BusinessException("当前管理员账号还没有绑定店铺，请先在「店铺管理」完善店铺信息");
            shopId = own.getId();
        }
        Product p = new Product();
        BeanUtils.copyProperties(dto, p);
        p.setShopId(shopId);
        if (p.getSales() == null) p.setSales(0);
        if (p.getStatus() == null) p.setStatus(1);
        if (p.getStock() == null) p.setStock(0);
        // 服务承诺默认值与平台原有文案保持一致：包邮、48 小时发货、支持 7 天无理由
        if (p.getFreeShipping() == null) p.setFreeShipping(1);
        if (p.getShipHours() == null) p.setShipHours(48);
        if (p.getReturnDays() == null) p.setReturnDays(7);
        productMapper.insert(p);
    }

    @Override
    @Transactional
    public void update(Long id, ProductDTO dto, Long operatorShopId) {
        Product old = productMapper.selectById(id);
        if (old == null) throw new BusinessException("商品不存在");
        checkShopOwner(old.getShopId(), operatorShopId);
        if (dto.getCategoryId() != null && categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BusinessException("所属分类不存在");
        }
        Product p = new Product();
        BeanUtils.copyProperties(dto, p);
        p.setId(id);
        p.setSales(old.getSales());
        // 商品归属不允许跨店调拨：商家固定为自己店铺，平台管理员保持商品原店铺
        p.setShopId(operatorShopId != null ? operatorShopId : old.getShopId());
        productMapper.updateById(p);
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorShopId) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException("商品不存在");
        checkShopOwner(p.getShopId(), operatorShopId);

        // 删除与下架是两个动作：
        // 下架 = status=0，商品仍保留在管理端列表；删除 = 逻辑删除，商品从管理端和用户端消失。
        // order_item 已保存商品名称/价格快照，所以删除商品不会影响历史订单展示。
        jdbcTemplate.update("DELETE FROM cart WHERE product_id = ?", id);
        jdbcTemplate.update("DELETE FROM favorite WHERE product_id = ?", id);
        jdbcTemplate.update("DELETE FROM browse_history WHERE product_id = ?", id);
        // 评价与 ReviewServiceImpl.cleanUpAndDelete 保持同一套语义：清追评 / 屏蔽记录，
        // 评价本体保留墓碑（deleted = id），不做物理删除
        jdbcTemplate.update("DELETE FROM review_append WHERE review_id IN (SELECT id FROM review WHERE product_id = ?)", id);
        jdbcTemplate.update("DELETE FROM review_hidden WHERE review_id IN (SELECT id FROM review WHERE product_id = ?)", id);
        jdbcTemplate.update("UPDATE review SET deleted = id WHERE product_id = ? AND deleted = 0", id);

        productMapper.deleteById(id);
    }

    @Override
    public List<Category> categoryList() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
    }

    @Override
    @Transactional
    public Long createCategory(CategoryDTO dto) {
        Long dup = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, dto.getName()));
        if (dup != null && dup > 0) throw new BusinessException("分类名称已存在");
        Category c = new Category();
        BeanUtils.copyProperties(dto, c);
        if (c.getParentId() == null) c.setParentId(0L);
        if (c.getSortOrder() == null) c.setSortOrder(0);
        categoryMapper.insert(c);
        return c.getId();
    }

    @Override
    @Transactional
    public void updateCategory(Long id, CategoryDTO dto) {
        Category old = categoryMapper.selectById(id);
        if (old == null) throw new BusinessException("分类不存在");
        Category c = new Category();
        BeanUtils.copyProperties(dto, c);
        c.setId(id);
        categoryMapper.updateById(c);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw new BusinessException("分类不存在");
        Long used = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (used != null && used > 0) {
            throw new BusinessException("该分类下还有 " + used + " 件商品，请先移走或删除商品");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public List<Product> hot(int limit) {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .inSql(Product::getShopId, "SELECT id FROM shop WHERE status = 1 AND deleted = 0")
                .orderByDesc(Product::getSales)
                .orderByDesc(Product::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
        fillShopNames(list);
        return list;
    }

    @Override
    public List<Product> lowStock(int threshold) {
        List<Product> list = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .le(Product::getStock, Math.max(0, threshold))
                .orderByAsc(Product::getStock)
                .last("LIMIT 20"));
        fillShopNames(list);
        return list;
    }

    /**
     * 只能操作自己店铺的商品：商家=本店；
     * 超管的 currentShopId 为 null（平台视角），这里回退到自己名下的店铺，
     * 别家店的商品仅可查看，不能改价 / 上下架 / 删除。
     */
    private void checkShopOwner(Long productShopId, Long operatorShopId) {
        Long scoped = operatorShopId;
        if (scoped == null) {
            com.ecommerce.entity.Shop own = shopService.mine();
            if (own == null) {
                throw new BusinessException(403, "当前账号还没有绑定店铺，无法操作商品");
            }
            scoped = own.getId();
        }
        if (!scoped.equals(productShopId)) {
            throw new BusinessException(403, "无权操作其他店铺的商品");
        }
    }

    /** 批量回填店铺名，别让前端额外请求 */
    private void fillShopNames(List<Product> list) {
        if (list == null || list.isEmpty()) return;
        java.util.List<Long> shopIds = list.stream().map(Product::getShopId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        java.util.Map<Long, String> names = shopService.nameMap(shopIds);
        list.forEach(p -> p.setShopName(names.get(p.getShopId())));
    }

    /** 清洗搜索关键词：限制长度并移除 LIKE 通配符，防止 "%" 匹配全表 */
    private String sanitizeKeyword(String keyword) {
        String s = keyword.trim();
        if (s.length() > 50) s = s.substring(0, 50);
        return s.replace("%", "").replace("_", "").replace("\\", "");
    }
}

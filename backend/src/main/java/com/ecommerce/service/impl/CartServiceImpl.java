package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.Shop;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.CartMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.service.CartService;
import com.ecommerce.service.ShopService;
import com.ecommerce.vo.CartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    /** 单次购买数量上限 */
    private static final int MAX_QUANTITY = 999;

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ShopService shopService;

    @Override
    public List<CartVO> list(Long userId) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreatedAt));
        if (carts.isEmpty()) return List.of();

        List<Long> productIds = carts.stream().map(Cart::getProductId).distinct().toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        List<CartVO> result = new ArrayList<>();
        for (Cart c : carts) {
            Product p = productMap.get(c.getProductId());
            if (p == null) continue;
            CartVO v = new CartVO();
            v.setId(c.getId());
            v.setUserId(c.getUserId());
            v.setProductId(c.getProductId());
            v.setShopId(p.getShopId());
            v.setProductName(p.getName());
            v.setProductCover(p.getCover());
            v.setPrice(p.getPrice());
            v.setStock(nvl(p.getStock()));
            v.setQuantity(nvl(c.getQuantity()) < 1 ? 1 : c.getQuantity());
            v.setSelected(c.getSelected() == null ? 1 : c.getSelected());
            BigDecimal price = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
            v.setSubtotal(price.multiply(BigDecimal.valueOf(v.getQuantity())).setScale(2, RoundingMode.HALF_UP));
            result.add(v);
        }
        // 回填店铺名，前端据此按店铺分组
        Map<Long, String> shopNames = shopService.nameMap(
                result.stream().map(CartVO::getShopId).toList());
        result.forEach(v -> v.setShopName(shopNames.get(v.getShopId())));
        return result;
    }

    @Override
    @Transactional
    public void add(Long userId, Long productId, Integer quantity) {
        int qty = checkQuantity(quantity);

        Product p = productMapper.selectById(productId);
        if (p == null) throw new BusinessException("商品不存在");
        if (p.getStatus() != null && p.getStatus() != 1) throw new BusinessException("商品已下架");
        // 关店店铺的商品在列表里是隐藏的，但收藏 / 足迹 / 旧链接仍可能进来加购，这里提前拦住
        if (p.getShopId() != null) {
            Shop shop = shopService.detail(p.getShopId());
            if (shop == null || shop.getStatus() == null || shop.getStatus() != 1) {
                throw new BusinessException("店铺暂停营业，暂时无法加购");
            }
        }

        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, productId));
        if (exist != null) {
            int newQty = nvl(exist.getQuantity()) + qty;
            if (newQty > MAX_QUANTITY) throw new BusinessException("单件商品最多购买 " + MAX_QUANTITY + " 件");
            if (newQty > nvl(p.getStock())) throw new BusinessException("库存不足");
            exist.setQuantity(newQty);
            cartMapper.updateById(exist);
        } else {
            if (qty > nvl(p.getStock())) throw new BusinessException("库存不足");
            // 复用被逻辑删除的行，别让 user_id + product_id 唯一索引冲突
            if (cartMapper.restoreDeleted(userId, productId, qty) == 0) {
                Cart c = new Cart();
                c.setUserId(userId);
                c.setProductId(productId);
                c.setQuantity(qty);
                c.setSelected(1);
                cartMapper.insert(c);
            }
        }
    }

    @Override
    public void updateQuantity(Long userId, Long cartId, Integer quantity) {
        int qty = checkQuantity(quantity);

        Cart c = cartMapper.selectById(cartId);
        if (c == null || !c.getUserId().equals(userId)) throw new BusinessException("购物车项不存在");
        Product p = productMapper.selectById(c.getProductId());
        if (p == null) throw new BusinessException("商品不存在");
        if (qty > nvl(p.getStock())) throw new BusinessException("库存不足");
        c.setQuantity(qty);
        cartMapper.updateById(c);
    }

    @Override
    public void updateSelected(Long userId, Long cartId, Integer selected) {
        if (selected == null || (selected != 0 && selected != 1)) {
            throw new BusinessException("选中状态不合法");
        }
        Cart c = cartMapper.selectById(cartId);
        if (c == null || !c.getUserId().equals(userId)) throw new BusinessException("购物车项不存在");
        c.setSelected(selected);
        cartMapper.updateById(c);
    }

    @Override
    public void delete(Long userId, Long cartId) {
        Cart c = cartMapper.selectById(cartId);
        if (c == null || !c.getUserId().equals(userId)) throw new BusinessException("购物车项不存在");
        cartMapper.deleteById(cartId);
    }

    @Override
    @Transactional
    public List<CartVO> checkout(Long userId) {
        List<CartVO> items = list(userId).stream()
                .filter(v -> v.getSelected() != null && v.getSelected() == 1)
                .toList();
        if (items.isEmpty()) throw new BusinessException("请先选择要结算的商品");

        // 校验库存与上架状态
        for (CartVO v : items) {
            Product p = productMapper.selectById(v.getProductId());
            if (p == null) throw new BusinessException("商品 " + v.getProductName() + " 已不存在");
            if (p.getStatus() != null && p.getStatus() != 1) {
                throw new BusinessException("商品 " + p.getName() + " 已下架，请移出购物车");
            }
            if (nvl(p.getStock()) < nvl(v.getQuantity())) {
                throw new BusinessException("商品 " + v.getProductName() + " 库存不足");
            }
        }
        return items;
    }

    private int checkQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) throw new BusinessException("购买数量必须大于 0");
        if (quantity > MAX_QUANTITY) throw new BusinessException("单件商品最多购买 " + MAX_QUANTITY + " 件");
        return quantity;
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}

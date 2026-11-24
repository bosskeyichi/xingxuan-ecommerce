package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.ChangePasswordDTO;
import com.ecommerce.dto.LoginDTO;
import com.ecommerce.dto.ProfileDTO;
import com.ecommerce.dto.RegisterDTO;
import com.ecommerce.entity.Coupon;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Orders;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserCoupon;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.mapper.OrderItemMapper;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.mapper.UserCouponMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.entity.Shop;
import com.ecommerce.service.MessageService;
import com.ecommerce.service.ShopService;
import com.ecommerce.service.UserService;
import com.ecommerce.utils.JwtUtil;
import com.ecommerce.utils.PasswordUtil;
import com.ecommerce.utils.SessionStore;
import com.ecommerce.vo.LoginVO;
import com.ecommerce.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 重置数据后恢复的默认密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final SessionStore sessionStore;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final ProductMapper productMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ShopService shopService;
    private final MessageService messageService;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 粘贴账号时容易带首尾空格，统一去掉再比对
        String account = dto.getUsername() == null ? "" : dto.getUsername().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account));
        if (user == null) {
            // 兼容用昵称登录：后台右上角显示的是昵称（如「超级管理员」），
            // 用户很容易直接拿它当账号；昵称可能重复，只有唯一匹配时才认可
            List<User> byNickname = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getNickname, account));
            if (byNickname.size() == 1) user = byNickname.get(0);
        }
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        // 生成 token + 写入 session：单设备登录按「账号 + 登录端」覆盖，前台与后台互不干扰
        String scope = SessionStore.normalize(dto.getScope());
        // 后台登录只对管理员 / 商家开放：普通用户不能靠 scope=ADMIN 预先占一个后台会话
        if (SessionStore.SCOPE_ADMIN.equals(scope)
                && !"ADMIN".equals(user.getRole()) && !"MERCHANT".equals(user.getRole())) {
            throw new BusinessException(403, "该账号没有后台权限，请联系超级管理员");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole(), scope);
        sessionStore.put(SessionStore.key(user.getId(), scope), token);

        log.info("用户 {} 登录，userId={}, role={}, scope={}", user.getUsername(), user.getId(), user.getRole(), scope);

        // 商家账号回填店铺信息，前端据此显示店铺名与数据范围
        Shop shop = shopService.getByOwner(user.getId());

        return LoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .shopId(shop == null ? null : shop.getId())
                .shopName(shop == null ? null : shop.getName())
                .token(token)
                .build();
    }

    @Override
    @Transactional
    public User register(RegisterDTO dto) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }
        // 邮箱若填写则校验唯一（空邮箱不限制，允许多个用户不填邮箱）
        if (StringUtils.hasText(dto.getEmail())) {
            Long eCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, dto.getEmail().trim()));
            if (eCount != null && eCount > 0) {
                throw new BusinessException("该邮箱已被注册");
            }
        }
        User u = new User();
        u.setUsername(dto.getUsername());
        u.setPassword(PasswordUtil.encode(dto.getPassword()));
        u.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        u.setEmail(dto.getEmail());
        u.setRole("USER");
        u.setStatus(1);
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            // 并发注册同一用户名的兜底，返回友好提示而不是 500
            throw new BusinessException("用户名已存在");
        }
        u.setPassword(null);
        return u;
    }

    @Override
    public User getById(Long id) {
        User u = userMapper.selectById(id);
        if (u != null) u.setPassword(null);
        return u;
    }

    @Override
    public User getCurrent() {
        Long uid = LoginInterceptor.USER_ID.get();
        if (uid == null) throw new BusinessException(401, "未登录");
        return getById(uid);
    }

    @Override
    public List<User> listAll() {
        List<User> list = userMapper.selectList(new LambdaQueryWrapper<User>()
                .orderByDesc(User::getId));
        list.forEach(u -> u.setPassword(null));
        return list;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法");
        }
        User target = userMapper.selectById(id);
        if (target == null) throw new BusinessException("用户不存在");

        Long operator = LoginInterceptor.USER_ID.get();
        if (operator != null && operator.equals(id)) {
            throw new BusinessException("不能修改自己的账号状态");
        }
        // 不允许禁用最后一个管理员，别让后台失去入口
        if ("ADMIN".equals(target.getRole()) && status == 0) {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, "ADMIN").eq(User::getStatus, 1));
            if (adminCount != null && adminCount <= 1) {
                throw new BusinessException("至少需保留一个启用状态的管理员");
            }
        }

        User u = new User();
        u.setId(id);
        u.setStatus(status);
        userMapper.updateById(u);

        // 禁用时立即踢下线，别让 token 在有效期内继续可用（两端一起清）
        if (status == 0) {
            sessionStore.removeAll(id);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User target = userMapper.selectById(id);
        if (target == null) throw new BusinessException("用户不存在");

        Long operator = LoginInterceptor.USER_ID.get();
        if (operator != null && operator.equals(id)) {
            throw new BusinessException("不能删除自己的账号");
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException("不能删除管理员账号");
        }
        // 店铺店主不能直接删除，否则店铺归属悬空、后台无法再进店管理
        boolean ownsShop = shopService.adminList().stream()
                .anyMatch(v -> id.equals(v.getOwnerId()));
        if (ownsShop) {
            throw new BusinessException("该账号是店铺店主，请先在「店铺管理」调整归属或关店后再删除");
        }
        // 删除账号前先清空其业务数据：回补库存/销量、收回优惠券发行量，
        // 别让留下订单、评价、浏览足迹等孤儿数据
        clearUserData(id, true);
        userMapper.deleteById(id);
        sessionStore.removeAll(id);
        log.info("管理员 {} 删除了用户 {}，并清理其业务数据", operator, target.getUsername());
    }

    @Override
    @Transactional
    public void resetData(Long id) {
        User target = userMapper.selectById(id);
        if (target == null) throw new BusinessException("用户不存在");

        Long operator = LoginInterceptor.USER_ID.get();
        if (operator != null && operator.equals(id)) {
            throw new BusinessException("不能重置自己的账号数据");
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException("不能重置管理员账号");
        }

        // 商家（普通管理员）：先清空店铺内容——下架全部商品、删除本店优惠券、店铺资料恢复默认
        List<Long> shopIds = jdbcTemplate.queryForList(
                "SELECT id FROM shop WHERE owner_id = ? AND deleted = 0", Long.class, id);
        for (Long shopId : shopIds) {
            jdbcTemplate.update("DELETE FROM user_coupon WHERE coupon_id IN (SELECT id FROM coupon WHERE shop_id = ?)", shopId);
            jdbcTemplate.update("DELETE FROM coupon WHERE shop_id = ?", shopId);
            jdbcTemplate.update("UPDATE product SET deleted = 1, status = 0 WHERE shop_id = ? AND deleted = 0", shopId);
            jdbcTemplate.update("UPDATE shop SET name = ?, intro = ?, cover = NULL WHERE id = ?",
                    target.getUsername() + "的店铺", "这家店还没有填写店铺简介。", shopId);
        }

        // 清空全部业务数据（含浏览足迹、追评、屏蔽记录），并回补库存 / 优惠券发行量
        clearUserData(id, true);

        // 账号资料与密码恢复默认（登录密码回到 123456），用户名保留
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .set(User::getNickname, target.getUsername())
                .set(User::getEmail, null)
                .set(User::getPhone, null)
                .set(User::getAvatar, null)
                .set(User::getPassword, PasswordUtil.encode(DEFAULT_PASSWORD))
                .eq(User::getId, id));

        // 强制下线，让用户用默认密码重新登录（两端一起清）
        sessionStore.removeAll(id);
        log.info("管理员 {} 重置了用户 {}：业务数据已清空，密码恢复默认，处理店铺 {} 家",
                operator, target.getUsername(), shopIds.size());
    }

    @Override
    @Transactional
    public void changeRole(Long id, String role) {
        User target = userMapper.selectById(id);
        if (target == null) throw new BusinessException("用户不存在");
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException("超级管理员账号不能调整角色");
        }
        String next = role == null ? "" : role.trim().toUpperCase();
        if (!"MERCHANT".equals(next) && !"USER".equals(next)) {
            throw new BusinessException("角色只能是 MERCHANT（普通管理员）或 USER（普通用户）");
        }
        if (next.equals(target.getRole())) return;

        List<Long> shopIds = jdbcTemplate.queryForList(
                "SELECT id FROM shop WHERE owner_id = ? AND deleted = 0", Long.class, id);

        if ("MERCHANT".equals(next)) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getRole, "MERCHANT").eq(User::getId, id));
            if (shopIds.isEmpty()) {
                String owner = target.getNickname() == null || target.getNickname().isBlank()
                        ? target.getUsername() : target.getNickname();
                jdbcTemplate.update(
                        "INSERT INTO shop(owner_id, name, intro, status) VALUES (?, ?, ?, 1)",
                        id, owner + "的店铺", "新晋商家的默认店铺，可在「店铺管理」中完善名称与简介。");
            }
            log.info("用户 {} 已提升为普通管理员（商家）", target.getUsername());
        } else {
            // 降级前必须清空店铺，否则商品/订单会失去归属
            if (!shopIds.isEmpty()) {
                Long shopId = shopIds.get(0);
                Integer products = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM product WHERE shop_id = ? AND deleted = 0", Integer.class, shopId);
                Integer orders = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `orders` WHERE shop_id = ? AND deleted = 0", Integer.class, shopId);
                if ((products != null && products > 0) || (orders != null && orders > 0)) {
                    throw new BusinessException("该账号的店铺还有商品或订单，请先「重置数据」清空店铺后再取消管理员");
                }
                jdbcTemplate.update("DELETE FROM coupon WHERE shop_id = ?", shopId);
                jdbcTemplate.update("DELETE FROM shop WHERE id = ?", shopId);
            }
            // 取消商家身份后，商家好友池不再适用，清掉双向好友关系
            jdbcTemplate.update("DELETE FROM friend WHERE user_id = ? OR friend_id = ?", id, id);
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .set(User::getRole, "USER").eq(User::getId, id));
            log.info("用户 {} 已取消普通管理员身份", target.getUsername());
        }
        // 角色变化后强制重新登录，别让旧 token 里仍带旧角色
        sessionStore.removeAll(id);
    }

    /**
     * 清空某个用户的全部业务数据（物理删除，别让逻辑删除残留唯一键）。
     * <p>
     * 覆盖：订单/明细、购物车、收货地址、收藏、评价（含追评与屏蔽记录）、
     * 已领优惠券、浏览足迹。调用前会回补未取消订单占用的库存与销量，
     * 并同步扣减优惠券的已领取数量。
     *
     * @param userId       用户 ID
     * @param restoreStock 是否回补库存/销量
     */
    private void clearUserData(Long userId, boolean restoreStock) {
        // 1. 回补库存与销量：取消订单在取消时已回补，跳过以免重复加库存
        if (restoreStock) {
            List<Orders> orders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                    .eq(Orders::getUserId, userId));
            if (!orders.isEmpty()) {
                Map<Long, String> statusMap = new HashMap<>();
                orders.forEach(o -> statusMap.put(o.getId(), o.getStatus()));
                List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, statusMap.keySet()));
                Map<Long, Integer> restoreQty = new HashMap<>();
                for (OrderItem it : items) {
                    if ("CANCELLED".equals(statusMap.get(it.getOrderId()))) continue;
                    int qty = it.getQuantity() == null ? 0 : Math.max(0, it.getQuantity());
                    if (qty > 0) restoreQty.merge(it.getProductId(), qty, Integer::sum);
                }
                restoreQty.forEach((pid, qty) -> productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .setSql("stock = stock + " + qty)
                        .setSql("sales = GREATEST(sales - " + qty + ", 0)")
                        .eq(Product::getId, pid)));
            }
        }

        // 2. 收回已领优惠券占用的发行量
        List<UserCoupon> ucs = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId));
        Map<Long, Integer> couponCount = new HashMap<>();
        ucs.forEach(uc -> couponCount.merge(uc.getCouponId(), 1, Integer::sum));
        couponCount.forEach((cid, cnt) -> couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .setSql("claimed = GREATEST(claimed - " + cnt + ", 0)")
                .eq(Coupon::getId, cid)));

        // 3. 物理删除业务数据（逻辑删除会残留唯一键，导致重置后无法再次加购 / 领券）
        jdbcTemplate.update("DELETE FROM review_append WHERE review_id IN (SELECT id FROM review WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM review_hidden WHERE review_id IN (SELECT id FROM review WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM `orders` WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM cart WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM address WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM favorite WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM review WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_coupon WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM browse_history WHERE user_id = ?", userId);
        // 社交数据：好友关系（双向）、消息（收发）、本人提交的举报、个人设置
        jdbcTemplate.update("DELETE FROM friend WHERE user_id = ? OR friend_id = ?", userId, userId);
        jdbcTemplate.update("DELETE FROM message WHERE from_id = ? OR to_id = ?", userId, userId);
        jdbcTemplate.update("DELETE FROM report WHERE reporter_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_setting WHERE user_id = ?", userId);
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, ProfileDTO dto) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException("用户不存在");
        if (dto.getNickname() != null) u.setNickname(dto.getNickname());
        if (dto.getEmail() != null) u.setEmail(dto.getEmail());
        if (dto.getPhone() != null) u.setPhone(dto.getPhone());
        if (dto.getAvatar() != null) u.setAvatar(dto.getAvatar());
        userMapper.updateById(u);
        u.setPassword(null);
        return u;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException("用户不存在");
        if (!PasswordUtil.matches(dto.getOldPassword(), u.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        User upd = new User();
        upd.setId(userId);
        upd.setPassword(PasswordUtil.encode(dto.getNewPassword()));
        userMapper.updateById(upd);
        // 修改密码后强制重新登录（两端一起清）
        sessionStore.removeAll(userId);
    }

    @Override
    public void notifyUser(Long toId, String content) {
        if (toId == null) throw new BusinessException("请选择接收用户");
        User target = userMapper.selectById(toId);
        if (target == null) throw new BusinessException("用户不存在");
        if (content == null || content.trim().isEmpty()) throw new BusinessException("通知内容不能为空");
        if (content.length() > 500) throw new BusinessException("通知内容过长（最多 500 字）");
        messageService.sendOfficial(toId, content.trim());
    }

    @Override
    public Page<User> page(long current, long size, String keyword) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            q.like(User::getUsername, keyword.trim())
                    .or().like(User::getNickname, keyword.trim());
        }
        q.orderByDesc(User::getId);
        Page<User> p = userMapper.selectPage(new Page<>(current, size), q);
        p.getRecords().forEach(u -> u.setPassword(null));

        // 标记商家账号：店铺 owner_id 与用户 id 关联，前端据此显示「商家 · 店铺名」
        Map<Long, ShopVO> shopByOwner = shopService.adminList().stream()
                .filter(v -> v.getOwnerId() != null)
                .collect(Collectors.toMap(ShopVO::getOwnerId, v -> v, (a, b) -> a));
        p.getRecords().forEach(u -> {
            ShopVO s = shopByOwner.get(u.getId());
            if (s != null) {
                u.setShopId(s.getId());
                u.setShopName(s.getName());
            }
        });
        return p;
    }
}

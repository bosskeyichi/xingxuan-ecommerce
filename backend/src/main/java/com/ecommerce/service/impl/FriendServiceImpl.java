package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.FriendAddDTO;
import com.ecommerce.entity.ConversationHide;
import com.ecommerce.entity.Friend;
import com.ecommerce.entity.Shop;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserSetting;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.ConversationHideMapper;
import com.ecommerce.mapper.FriendMapper;
import com.ecommerce.mapper.ShopMapper;
import com.ecommerce.mapper.UserMapper;
import com.ecommerce.mapper.UserSettingMapper;
import com.ecommerce.service.FriendService;
import com.ecommerce.service.MessageService;
import com.ecommerce.vo.UserCardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 好友关系实现（申请-同意制）。
 * 规则：
 * 1. 用户端（USER）之间互加；管理端商家（MERCHANT）之间互加；两端好友池隔离。
 * 2. 超级管理员（ADMIN）默认拥有全部商家好友，不需要也不能手动添加。
 * 3. 加好友需对方同意：先发申请，对方在「好友申请」里同意后双方才能聊天。
 * 4. 搜索必须带关键词，不允许不加好友就浏览全部用户（隐私保护）。
 */
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_MERCHANT = "MERCHANT";
    private static final String ROLE_ADMIN = "ADMIN";

    private final FriendMapper friendMapper;
    private final ConversationHideMapper conversationHideMapper;
    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final UserSettingMapper settingMapper;
    // MessageService 也依赖 FriendService，用 ObjectProvider 打破构造器循环依赖
    private final ObjectProvider<MessageService> messageServiceProvider;

    private MessageService messageService() {
        return messageServiceProvider.getObject();
    }

    @Override
    public IPage<UserCardVO> search(Long uid, String keyword, String filter, long current, long size) {
        User me = userMapper.selectById(uid);
        if (me == null) throw new BusinessException(401, "请先登录");

        // 隐私保护：不带关键词不允许浏览用户，必须主动搜索才能找到人
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return new Page<UserCardVO>(current, size, 0);
        }

        // 搜索池：用户端只搜用户；管理端搜商家，同时能搜到系统默认好友超级管理员
        List<String> targetRoles = new ArrayList<>();
        targetRoles.add(ROLE_USER.equals(me.getRole()) ? ROLE_USER : ROLE_MERCHANT);
        if (ROLE_MERCHANT.equals(me.getRole())) targetRoles.add(ROLE_ADMIN);

        String f = filter == null || filter.trim().isEmpty() ? "ALL" : filter.trim().toUpperCase();
        Long idMatch = null;
        try {
            idMatch = Long.parseLong(kw);
        } catch (NumberFormatException ignored) {
            // 关键词不是数字，只按昵称/用户名匹配
        }

        Set<Long> friendIds = friendIds(uid);
        if ("FRIEND".equals(f) && friendIds.isEmpty()) {
            return new Page<UserCardVO>(current, size, 0);
        }

        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<User>()
                .in(User::getRole, targetRoles)
                .eq(User::getStatus, 1)
                .ne(User::getId, uid);
        final Long idVal = idMatch;
        w.and(x -> {
            x.like(User::getNickname, kw).or().like(User::getUsername, kw);
            if (idVal != null) x.or().eq(User::getId, idVal);
        });
        // 对方关闭「允许被搜索」时不出现在结果里
        w.notInSql(User::getId, "SELECT user_id FROM user_setting WHERE deleted = 0 AND searchable = 0");

        // 分类筛选：可申请 / 已是好友
        if ("FRIEND".equals(f)) {
            w.in(User::getId, friendIds);
        } else if ("ADDABLE".equals(f)) {
            if (!friendIds.isEmpty()) w.notIn(User::getId, friendIds);
            w.apply("NOT EXISTS (SELECT 1 FROM user_setting s WHERE s.deleted = 0 AND s.user_id = user_table.id"
                    + " AND (s.searchable = 0 OR s.friend_policy = 'NOBODY'))");
        }
        w.orderByAsc(User::getId);

        Page<User> page = userMapper.selectPage(new Page<>(current, size), w);
        List<UserCardVO> records = toCards(page.getRecords(), uid, friendIds);
        return new Page<UserCardVO>(page.getCurrent(), page.getSize(), page.getTotal()).setRecords(records);
    }

    @Override
    public List<UserCardVO> listFriends(Long uid) {
        User me = userMapper.selectById(uid);
        if (me == null) throw new BusinessException(401, "请先登录");

        // 系统默认好友（虚拟关系，不落库）：超级管理员 ↔ 全部商家，双方都要默认存在
        List<User> systemUsers = new ArrayList<>();
        String peerRole = systemPeerRole(me);
        if (peerRole != null) {
            systemUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, peerRole)
                    .eq(User::getStatus, 1)
                    .orderByAsc(User::getId));
        }
        Set<Long> systemIds = systemUsers.stream().map(User::getId).collect(Collectors.toSet());

        // 申请-同意制落库的真实好友
        List<Long> ids = friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, uid)
                        .eq(Friend::getStatus, Friend.STATUS_ACCEPTED)
                        .orderByDesc(Friend::getId))
                .stream().map(Friend::getFriendId).filter(Objects::nonNull).collect(Collectors.toList());

        List<User> real = new ArrayList<>();
        if (!ids.isEmpty()) {
            real = userMapper.selectBatchIds(ids).stream()
                    .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                    .filter(u -> !systemIds.contains(u.getId()))
                    .collect(Collectors.toList());
            // 按好友添加顺序返回
            Map<Long, Integer> order = new HashMap<>();
            for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
            real.sort(Comparator.comparingInt(u -> order.getOrDefault(u.getId(), Integer.MAX_VALUE)));
        }
        if (systemUsers.isEmpty() && real.isEmpty()) return Collections.emptyList();

        List<User> all = new ArrayList<>(systemUsers);
        all.addAll(real);
        Set<Long> friendIds = new HashSet<>(ids);
        friendIds.addAll(systemIds);
        return toCards(all, uid, friendIds);
    }

    @Override
    @Transactional
    public void add(Long uid, FriendAddDTO dto) {
        if (dto.getUserId() == null) throw new BusinessException("请选择要添加的用户");
        if (dto.getUserId().equals(uid)) throw new BusinessException("不能添加自己为好友");

        User me = userMapper.selectById(uid);
        User target = userMapper.selectById(dto.getUserId());
        if (me == null || target == null) throw new BusinessException("用户不存在");
        if (target.getStatus() == null || target.getStatus() != 1) throw new BusinessException("该用户已被禁用");

        if (isFriend(uid, target.getId())) {
            throw new BusinessException("你们已经是好友了");
        }
        if (isSystemPair(me, target)) {
            throw new BusinessException("超级管理员与商家的好友关系为系统默认，无需添加");
        }
        boolean samePool = (ROLE_USER.equals(me.getRole()) && ROLE_USER.equals(target.getRole()))
                || (ROLE_MERCHANT.equals(me.getRole()) && ROLE_MERCHANT.equals(target.getRole()));
        if (!samePool) {
            throw new BusinessException("只能添加同类型的账号为好友");
        }

        UserSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, target.getId()).last("LIMIT 1"));
        if (setting != null && Integer.valueOf(0).equals(setting.getSearchable())) {
            throw new BusinessException(403, "对方未开放搜索，无法添加");
        }
        if (setting != null && "NOBODY".equals(setting.getFriendPolicy())) {
            throw new BusinessException(403, "对方设置了不允许添加好友");
        }

        // 我已经发过申请：不要重复发
        Friend mine = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, uid).eq(Friend::getFriendId, target.getId()).last("LIMIT 1"));
        if (mine != null && Integer.valueOf(Friend.STATUS_PENDING).equals(mine.getStatus())) {
            throw new BusinessException("已发送过好友申请，请等待对方处理");
        }

        // 对方已先申请了我：直接同意，双方立即成为好友
        Friend other = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, target.getId()).eq(Friend::getFriendId, uid)
                .eq(Friend::getStatus, Friend.STATUS_PENDING).last("LIMIT 1"));
        if (other != null) {
            other.setStatus(Friend.STATUS_ACCEPTED);
            friendMapper.updateById(other);
            insertPair(uid, target.getId(), dto.getRemark());
            messageService().sendSystem(target.getId(),
                    displayName(me) + " 已同意你的好友申请，你们现在可以开始聊天了。");
            return;
        }

        // 常规路径：写一条申请记录，等对方处理
        Friend apply = new Friend();
        apply.setUserId(uid);
        apply.setFriendId(target.getId());
        apply.setStatus(Friend.STATUS_PENDING);
        apply.setRemark(dto.getRemark());
        friendMapper.insert(apply);

        messageService().sendSystem(target.getId(),
                displayName(me) + " 请求加你为好友，请到「好友申请」中处理。");
    }

    @Override
    public UserCardVO card(Long uid, Long targetId) {
        if (targetId == null) throw new BusinessException("用户不存在");
        User target = userMapper.selectById(targetId);
        if (target == null) throw new BusinessException("用户不存在");

        Set<Long> friendIds = friendIds(uid);
        boolean self = targetId.equals(uid);
        boolean isFriend = self || friendIds.contains(targetId) || isFriend(uid, targetId);

        UserSetting st = settingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, targetId).last("LIMIT 1"));
        // 隐私：非好友且对方关闭「允许被搜索」时，连主页都不公开
        if (!self && !isFriend && st != null && Integer.valueOf(0).equals(st.getSearchable())) {
            throw new BusinessException(403, "对方未公开个人资料");
        }
        // 陌生人只看得到基础信息，联系方式严格按开关展示（toCards 已处理）
        UserCardVO vo = toCards(Collections.singletonList(target), uid, friendIds).get(0);
        // 跨端（用户 ↔ 商家）本来就不能互加好友，别在前端给出「申请」按钮再报错
        User me = userMapper.selectById(uid);
        if (!self && !samePool(me, target)) vo.setAddable(false);
        return vo;
    }

    /** 用户端与商家端的好友池隔离：只有同角色才能互加 */
    private boolean samePool(User a, User b) {
        if (a == null || b == null) return false;
        return (ROLE_USER.equals(a.getRole()) && ROLE_USER.equals(b.getRole()))
                || (ROLE_MERCHANT.equals(a.getRole()) && ROLE_MERCHANT.equals(b.getRole()))
                || (ROLE_ADMIN.equals(a.getRole()) && ROLE_MERCHANT.equals(b.getRole()))
                || (ROLE_MERCHANT.equals(a.getRole()) && ROLE_ADMIN.equals(b.getRole()));
    }

    @Override
    public List<UserCardVO> requests(Long uid) {
        // 申请记录只有一行（申请人 → 被申请人），所以按 friend_id = 我 来查收到的申请
        List<Long> requesterIds = friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getFriendId, uid)
                        .eq(Friend::getStatus, Friend.STATUS_PENDING)
                        .orderByDesc(Friend::getId))
                .stream().map(Friend::getUserId).filter(Objects::nonNull).collect(Collectors.toList());
        if (requesterIds.isEmpty()) return Collections.emptyList();

        List<User> users = userMapper.selectBatchIds(requesterIds).stream()
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .collect(Collectors.toList());
        return toCards(users, uid, Collections.emptySet());
    }

    @Override
    @Transactional
    public void accept(Long uid, Long requesterId) {
        Friend apply = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, requesterId).eq(Friend::getFriendId, uid)
                .eq(Friend::getStatus, Friend.STATUS_PENDING).last("LIMIT 1"));
        if (apply == null) throw new BusinessException("该好友申请不存在或已处理");

        apply.setStatus(Friend.STATUS_ACCEPTED);
        friendMapper.updateById(apply);
        insertPair(uid, requesterId, null);

        // 重新成为好友后恢复双方的历史会话显示
        restoreConversation(uid, requesterId);
        restoreConversation(requesterId, uid);

        User requester = userMapper.selectById(requesterId);
        messageService().sendSystem(requesterId,
                displayName(requester == null ? null : requester) + " 已同意你的好友申请，你们现在可以开始聊天了。");
    }

    private void restoreConversation(Long owner, Long peer) {
        conversationHideMapper.delete(new LambdaQueryWrapper<ConversationHide>()
                .eq(ConversationHide::getUserId, owner)
                .eq(ConversationHide::getConversationKey,
                        Math.min(owner, peer) + ":" + Math.max(owner, peer) + ":FRIEND"));
    }

    @Override
    @Transactional
    public void reject(Long uid, Long requesterId) {
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, requesterId).eq(Friend::getFriendId, uid)
                .eq(Friend::getStatus, Friend.STATUS_PENDING));
    }

    @Override
    @Transactional
    public void remove(Long uid, Long friendId) {
        User me = userMapper.selectById(uid);
        // 系统默认好友双向都不可删除（此前只挡了 ADMIN 一侧，商家端仍能删）
        if (me != null && isSystemPair(me, userMapper.selectById(friendId))) {
            throw new BusinessException("超级管理员与商家的好友关系为系统默认，无法删除");
        }
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, uid).eq(Friend::getFriendId, friendId));
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, friendId).eq(Friend::getFriendId, uid));
        // 删除好友后双方的好友会话都从列表消失（消息保留，重新加好友可继续查看历史）
        try {
            messageService().hideConversation(uid, friendId, "FRIEND");
            messageService().hideConversation(friendId, uid, "FRIEND");
        } catch (Exception ignored) {
            // 会话隐藏失败不影响删除好友
        }
    }

    @Override
    public Set<Long> friendIds(Long uid) {
        Set<Long> ids = friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, uid)
                        .eq(Friend::getStatus, Friend.STATUS_ACCEPTED))
                .stream().map(Friend::getFriendId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        User me = userMapper.selectById(uid);
        String peerRole = me == null ? null : systemPeerRole(me);
        if (peerRole != null) {
            userMapper.selectList(new LambdaQueryWrapper<User>()
                            .eq(User::getRole, peerRole)
                            .eq(User::getStatus, 1))
                    .forEach(u -> ids.add(u.getId()));
        }
        return ids;
    }

    /** 系统默认好友对端角色：超级管理员 ↔ 商家（其余角色没有默认好友） */
    private String systemPeerRole(User me) {
        if (ROLE_ADMIN.equals(me.getRole())) return ROLE_MERCHANT;
        if (ROLE_MERCHANT.equals(me.getRole())) return ROLE_ADMIN;
        return null;
    }

    /** 是否为系统默认好友关系（不可删除） */
    private boolean isSystemPair(User a, User b) {
        if (a == null || b == null) return false;
        return (ROLE_ADMIN.equals(a.getRole()) && ROLE_MERCHANT.equals(b.getRole()))
                || (ROLE_MERCHANT.equals(a.getRole()) && ROLE_ADMIN.equals(b.getRole()));
    }

    @Override
    public boolean isFriend(Long uid, Long targetId) {
        if (uid == null || targetId == null || uid.equals(targetId)) return false;

        User me = userMapper.selectById(uid);
        User target = userMapper.selectById(targetId);
        if (me == null || target == null) return false;
        // 超级管理员 ↔ 商家：默认好友
        if (ROLE_ADMIN.equals(me.getRole()) && ROLE_MERCHANT.equals(target.getRole())) return true;
        if (ROLE_ADMIN.equals(target.getRole()) && ROLE_MERCHANT.equals(me.getRole())) return true;

        Long count = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, uid)
                .eq(Friend::getFriendId, targetId)
                .eq(Friend::getStatus, Friend.STATUS_ACCEPTED));
        return count != null && count > 0;
    }

    private void insertPair(Long a, Long b, String remark) {
        Friend ab = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, a).eq(Friend::getFriendId, b).last("LIMIT 1"));
        if (ab == null) {
            ab = new Friend();
            ab.setUserId(a);
            ab.setFriendId(b);
        }
        ab.setStatus(Friend.STATUS_ACCEPTED);
        if (ab.getRemark() == null && remark != null) ab.setRemark(remark);
        if (ab.getId() == null) friendMapper.insert(ab); else friendMapper.updateById(ab);

        Friend ba = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, b).eq(Friend::getFriendId, a).last("LIMIT 1"));
        if (ba == null) {
            ba = new Friend();
            ba.setUserId(b);
            ba.setFriendId(a);
        }
        ba.setStatus(Friend.STATUS_ACCEPTED);
        if (ba.getId() == null) friendMapper.insert(ba); else friendMapper.updateById(ba);
    }

    private List<UserCardVO> toCards(List<User> users, Long uid, Set<Long> friendIds) {
        if (users == null || users.isEmpty()) return Collections.emptyList();
        Set<Long> ids = users.stream().map(User::getId).collect(Collectors.toSet());

        Map<Long, Shop> shopMap = shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                        .in(Shop::getOwnerId, ids))
                .stream().collect(Collectors.toMap(Shop::getOwnerId, s -> s, (a, b) -> a));

        Map<Long, UserSetting> settingMap = settingMapper.selectList(new LambdaQueryWrapper<UserSetting>()
                        .in(UserSetting::getUserId, ids))
                .stream().collect(Collectors.toMap(UserSetting::getUserId, s -> s, (a, b) -> a));

        // 我发出的、还在等待处理的申请
        Set<Long> pendingIds = friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                        .eq(Friend::getUserId, uid)
                        .eq(Friend::getStatus, Friend.STATUS_PENDING))
                .stream().map(Friend::getFriendId).collect(Collectors.toSet());

        User me = userMapper.selectById(uid);
        boolean meAdmin = me != null && ROLE_ADMIN.equals(me.getRole());
        boolean meMerchant = me != null && ROLE_MERCHANT.equals(me.getRole());

        List<UserCardVO> list = new ArrayList<>(users.size());
        for (User u : users) {
            UserSetting st = settingMap.get(u.getId());
            Shop shop = shopMap.get(u.getId());

            UserCardVO vo = new UserCardVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname() == null || u.getNickname().isEmpty() ? u.getUsername() : u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setRole(u.getRole());
            vo.setShopId(shop == null ? null : shop.getId());
            vo.setShopName(shop == null ? null : shop.getName());
            vo.setSystem((meAdmin && ROLE_MERCHANT.equals(u.getRole()))
                    || (meMerchant && ROLE_ADMIN.equals(u.getRole())));
            vo.setFriend(friendIds.contains(u.getId()) || Boolean.TRUE.equals(vo.getSystem()));
            vo.setPending(pendingIds.contains(u.getId()));
            vo.setSelf(u.getId().equals(uid));

            boolean canSearch = st == null || !Integer.valueOf(0).equals(st.getSearchable());
            boolean canFriend = st == null || !"NOBODY".equals(st.getFriendPolicy());
            vo.setAddable(canSearch && canFriend);
            // 自己看自己的主页时不受隐私开关限制
            boolean selfCard = u.getId().equals(uid);
            boolean mailOn = selfCard || (st != null && Integer.valueOf(1).equals(st.getShowEmail()));
            boolean phoneOn = selfCard || (st != null && Integer.valueOf(1).equals(st.getShowPhone()));
            vo.setEmail(mailOn ? u.getEmail() : null);
            vo.setPhone(phoneOn ? u.getPhone() : null);
            list.add(vo);
        }
        return list;
    }

    private String displayName(User u) {
        if (u == null) return "有用户";
        return u.getNickname() == null || u.getNickname().isEmpty() ? u.getUsername() : u.getNickname();
    }
}

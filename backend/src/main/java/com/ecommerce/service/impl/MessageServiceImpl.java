package com.ecommerce.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.dto.MessageSendDTO;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.mapper.*;
import com.ecommerce.service.FriendService;
import com.ecommerce.service.MessageService;
import com.ecommerce.service.SettingService;
import com.ecommerce.vo.ConversationVO;
import com.ecommerce.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final String SCENE_FRIEND = "FRIEND";
    private static final String SCENE_SERVICE = "SERVICE";
    private static final String SCENE_SYSTEM = "SYSTEM";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("TEXT", "PRODUCT", "SHOP", "REVIEW"));
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final MessageMapper messageMapper;
    private final ConversationHideMapper conversationHideMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final ReviewMapper reviewMapper;
    private final FriendService friendService;
    private final SettingService settingService;

    @Override
    public List<ConversationVO> conversations(Long uid, String scene) {
        String only = scene == null || scene.trim().isEmpty() ? null : scene.trim().toUpperCase();

        // 我参与的全部消息，按时间倒序；内存按会话分组取最新一条
        List<Message> messages = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .and(w -> w.eq(Message::getFromId, uid).or().eq(Message::getToId, uid))
                .ne(Message::getScene, SCENE_SYSTEM)
                .orderByDesc(Message::getId));

        // 我隐藏过的会话（从列表删除过）：最后一条消息 id 没超过隐藏点的不再显示
        Map<String, Long> hideMap = hideMap(uid);

        Map<String, ConversationVO> map = new LinkedHashMap<>();
        Map<String, Integer> unreadMap = new HashMap<>();
        Map<String, Message> lastMap = new HashMap<>();
        for (Message m : messages) {
            String key = m.getConversationKey();
            lastMap.putIfAbsent(key, m);
            if (m.getToId().equals(uid) && m.getReadAt() == null) {
                unreadMap.merge(key, 1, Integer::sum);
            }
        }

        Set<Long> peerIds = new HashSet<>();
        for (Map.Entry<String, Message> e : lastMap.entrySet()) {
            Message m = e.getValue();
            peerIds.add(m.getFromId().equals(uid) ? m.getToId() : m.getFromId());
        }

        Map<Long, User> users = users(peerIds);
        Map<Long, Shop> shops = shopsOf(peerIds);
        // 好友关系只查一次，别让逐条会话查库
        Set<Long> myFriendIds = friendService.friendIds(uid);

        for (Map.Entry<String, Message> e : lastMap.entrySet()) {
            Message m = e.getValue();
            // 已删除（隐藏）的会话：对方没有新消息时不再出现
            Long hidePoint = hideMap.get(e.getKey());
            if (hidePoint != null && m.getId() != null && m.getId() <= hidePoint) continue;

            Long peerId = m.getFromId().equals(uid) ? m.getToId() : m.getFromId();
            // 好友被删除后，旧的好友会话不再显示（对方也打不开，需重新加好友）
            if (SCENE_FRIEND.equals(m.getScene()) && !myFriendIds.contains(peerId)) continue;

            User peer = users.get(peerId);
            ConversationVO vo = new ConversationVO();
            vo.setKey(e.getKey());
            vo.setScene(m.getScene());
            vo.setPeerId(peerId);
            vo.setPeerName(displayName(peer, peerId));
            vo.setPeerHandle(peer == null ? null : peer.getUsername());
            vo.setPeerAvatar(peer == null ? null : peer.getAvatar());
            vo.setLastContent(preview(m));
            vo.setLastTime(m.getCreatedAt() == null ? null : m.getCreatedAt().format(FMT));
            vo.setLastAt(m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
            vo.setUnread(unreadMap.getOrDefault(e.getKey(), 0));
            vo.setShopId(m.getShopId());
            Shop shop = m.getShopId() == null ? null : shops.get(m.getShopId());
            vo.setShopName(shop == null ? null : shop.getName());
            vo.setFriend(myFriendIds.contains(peerId));
            map.put(e.getKey(), vo);
        }

        // 好友但还没有聊天记录：补空会话，方便点开就聊
        if (only == null || SCENE_FRIEND.equals(only)) {
            // 修复：批量查询好友资料，此前依赖「有消息的 peer」缓存，没有任何消息时所有空会话都会被跳过
            Map<Long, User> friendUsers = users(myFriendIds);
            for (Long friendId : myFriendIds) {
                String key = convKey(uid, friendId, SCENE_FRIEND);
                if (map.containsKey(key)) continue;
                if (hideMap.containsKey(key)) continue;
                User u = friendUsers.get(friendId);
                if (u == null) continue;
                ConversationVO vo = new ConversationVO();
                vo.setKey(key);
                vo.setScene(SCENE_FRIEND);
                vo.setPeerId(friendId);
                vo.setPeerName(displayName(u, friendId));
                vo.setPeerHandle(u.getUsername());
                vo.setPeerAvatar(u.getAvatar());
                vo.setLastContent("");
                vo.setLastTime(null);
                vo.setUnread(0);
                vo.setFriend(true);
                map.put(key, vo);
            }
        }

        List<ConversationVO> list = map.values().stream()
                .filter(c -> only == null || only.equals(c.getScene()))
                // 有消息的按时间倒序，没有消息的好友排在后面
                .sorted((a, b) -> {
                    if (a.getLastAt() == null && b.getLastAt() == null) return 0;
                    if (a.getLastAt() == null) return 1;
                    if (b.getLastAt() == null) return -1;
                    return b.getLastAt().compareTo(a.getLastAt());
                })
                .collect(Collectors.toList());
        return list;
    }

    @Override
    public IPage<MessageVO> history(Long uid, Long peerId, String scene, long current, long size) {
        String sc = scene == null || scene.trim().isEmpty() ? SCENE_FRIEND : scene.trim().toUpperCase();
        if (!SCENE_FRIEND.equals(sc) && !SCENE_SERVICE.equals(sc)) {
            throw new BusinessException("会话类型不合法");
        }
        if (!canAccess(uid, peerId, sc)) {
            throw new BusinessException(403, "无权查看该会话");
        }
        String key = convKey(uid, peerId, sc);
        Page<Message> page = messageMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationKey, key)
                        .orderByDesc(Message::getId));
        List<MessageVO> vos = toVOs(page.getRecords(), uid);
        return new Page<MessageVO>(page.getCurrent(), page.getSize(), page.getTotal()).setRecords(vos);
    }

    @Override
    public MessageVO send(Long uid, MessageSendDTO dto) {
        User me = userMapper.selectById(uid);
        if (me == null) throw new BusinessException(401, "请先登录");

        String scene = dto.sceneOrDefault();
        String type = dto.getType() == null || dto.getType().trim().isEmpty()
                ? "TEXT" : dto.getType().trim().toUpperCase();
        if (!TYPES.contains(type)) throw new BusinessException("消息类型不合法");
        dto.validate();

        Long toId;
        Long shopId = null;
        if (SCENE_SERVICE.equals(scene)) {
            Shop shop = resolveServiceShop(uid, dto);
            shopId = shop.getId();
            boolean owner = shop.getOwnerId().equals(uid);
            toId = owner ? dto.getToId() : shop.getOwnerId();
            if (toId == null) throw new BusinessException("请选择要回复的用户");
            if (toId.equals(uid)) throw new BusinessException("不能给自己发消息");
        } else if (SCENE_FRIEND.equals(scene)) {
            toId = dto.getToId();
            if (toId == null) throw new BusinessException("请选择接收人");
            if (toId.equals(uid)) throw new BusinessException("不能给自己发消息");
            if (!friendService.isFriend(uid, toId)) {
                throw new BusinessException(403, "对方还不是你的好友");
            }
        } else {
            throw new BusinessException("会话类型不合法");
        }

        // 超级管理员代表平台官方：对方关掉「接收好友私聊 / 客服消息」也必须能收到官方消息
        if (!isOfficialSender(uid) && !settingService.canReceive(toId, scene)) {
            throw new BusinessException("对方已关闭该类消息的接收");
        }

        Message m = new Message();
        m.setConversationKey(convKey(uid, toId, scene));
        m.setScene(scene);
        m.setFromId(uid);
        m.setToId(toId);
        m.setType(type);
        m.setContent(dto.getContent() == null ? null : dto.getContent().trim());
        m.setTargetId("TEXT".equals(type) ? null : dto.getTargetId());
        m.setShopId(shopId);
        m.setReadAt(null);
        messageMapper.insert(m);

        // 重新查一次拿数据库生成的时间
        Message saved = messageMapper.selectById(m.getId());
        return toVOs(Collections.singletonList(saved == null ? m : saved), uid).get(0);
    }

    @Override
    public int markRead(Long uid, Long peerId, String scene) {
        String sc = scene == null || scene.trim().isEmpty() ? SCENE_FRIEND : scene.trim().toUpperCase();
        return messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getConversationKey, convKey(uid, peerId, sc))
                .eq(Message::getToId, uid)
                .isNull(Message::getReadAt)
                .set(Message::getReadAt, LocalDateTime.now()));
    }

    @Override
    public boolean hideConversation(Long uid, Long peerId, String scene) {
        String sc = scene == null || scene.trim().isEmpty() ? SCENE_FRIEND : scene.trim().toUpperCase();
        if (!SCENE_FRIEND.equals(sc) && !SCENE_SERVICE.equals(sc)) {
            throw new BusinessException("会话类型不合法");
        }
        String key = convKey(uid, peerId, sc);
        // 记录隐藏点 = 会话内当前最大消息 id；之后对方的新消息（id 更大）会让会话重新出现
        Message last = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationKey, key)
                .orderByDesc(Message::getId)
                .last("LIMIT 1"));
        long hidePoint = last == null || last.getId() == null ? 0L : last.getId();

        ConversationHide hide = conversationHideMapper.selectOne(new LambdaQueryWrapper<ConversationHide>()
                .eq(ConversationHide::getUserId, uid)
                .eq(ConversationHide::getConversationKey, key)
                .last("LIMIT 1"));
        if (hide == null) {
            hide = new ConversationHide();
            hide.setUserId(uid);
            hide.setConversationKey(key);
        }
        // 已有隐藏点取较大值，别让把早前隐藏时没看到的新消息直接吞掉
        if (hide.getLastMessageId() != null && hide.getLastMessageId() > hidePoint) {
            hidePoint = hide.getLastMessageId();
        }
        hide.setLastMessageId(hidePoint);
        if (hide.getId() == null) conversationHideMapper.insert(hide);
        else conversationHideMapper.updateById(hide);
        return true;
    }

    @Override
    public void markSystemRead(Long uid) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getConversationKey, "SYS:" + uid)
                .isNull(Message::getReadAt)
                .set(Message::getReadAt, LocalDateTime.now()));
    }

    @Override
    public Map<String, Object> peerCard(Long uid, Long peerId, String scene) {
        if (peerId == null) throw new BusinessException("缺少对方信息");
        User peer = userMapper.selectById(peerId);
        if (peer == null) throw new BusinessException("用户不存在");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("peerId", peer.getId());
        data.put("peerName", displayName(peer, peer.getId()));
        data.put("peerHandle", peer.getUsername());
        data.put("peerAvatar", peer.getAvatar());
        data.put("role", peer.getRole());

        // 客服会话标题展示店铺，好友会话也带上对方店铺用来展示身份
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getOwnerId, peerId).last("LIMIT 1"));
        data.put("shopId", shop == null ? null : shop.getId());
        data.put("shopName", shop == null ? null : shop.getName());
        data.put("friend", friendService.isFriend(uid, peerId));
        return data;
    }

    private Map<String, Long> hideMap(Long uid) {
        return conversationHideMapper.selectList(new LambdaQueryWrapper<ConversationHide>()
                        .eq(ConversationHide::getUserId, uid))
                .stream()
                .filter(h -> h.getConversationKey() != null)
                .collect(Collectors.toMap(
                        ConversationHide::getConversationKey,
                        h -> h.getLastMessageId() == null ? 0L : h.getLastMessageId(),
                        (a, b) -> Math.max(a, b)));
    }

    @Override
    public Map<String, Object> unreadSummary(Long uid) {
        List<Message> list = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getToId, uid)
                .isNull(Message::getReadAt));
        // 口径必须与会话列表一致：已删除（隐藏）的会话、已不是好友的会话不计入，
        // 否则会出现「角标显示 1，但列表里找不到任何未读会话」的情况
        Map<String, Long> hideMap = hideMap(uid);
        Set<Long> friendIds = friendService.friendIds(uid);
        int total = 0, friend = 0, service = 0, system = 0;
        for (Message m : list) {
            Long hidePoint = hideMap.get(m.getConversationKey());
            if (hidePoint != null && m.getId() != null && m.getId() <= hidePoint) continue;
            if (SCENE_FRIEND.equals(m.getScene())) {
                Long peer = m.getFromId();
                if (peer != null && !friendIds.contains(peer)) continue;
            }
            total++;
            if (SCENE_SERVICE.equals(m.getScene())) service++;
            else if (SCENE_SYSTEM.equals(m.getScene())) system++;
            else friend++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("FRIEND", friend);
        data.put("SERVICE", service);
        data.put("SYSTEM", system);
        return data;
    }

    @Override
    public IPage<MessageVO> systemMessages(Long uid, long current, long size) {
        Page<Message> page = messageMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationKey, "SYS:" + uid)
                        .orderByDesc(Message::getId));
        List<MessageVO> vos = toVOs(page.getRecords(), uid);
        // 打开「系统通知」即视为全部看过：此前不标记已读，导致侧边栏角标永远清不掉
        markSystemRead(uid);
        return new Page<MessageVO>(page.getCurrent(), page.getSize(), page.getTotal()).setRecords(vos);
    }

    @Override
    public Map<String, Object> serviceTarget(Long shopId) {
        Shop shop = shopId == null ? null : shopMapper.selectById(shopId);
        if (shop == null) throw new BusinessException("店铺不存在");
        User owner = userMapper.selectById(shop.getOwnerId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("shopId", shop.getId());
        data.put("shopName", shop.getName());
        data.put("shopStatus", shop.getStatus());
        data.put("userId", shop.getOwnerId());
        data.put("nickname", owner == null ? "店铺客服"
                : (owner.getNickname() == null || owner.getNickname().isEmpty() ? owner.getUsername() : owner.getNickname()));
        data.put("avatar", owner == null ? null : owner.getAvatar());
        data.put("online", owner != null && Integer.valueOf(1).equals(owner.getStatus()));
        return data;
    }

    @Override
    public void sendSystem(Long toId, String content) {
        // 系统通知（好友申请 / 举报结果等事务性通知）强制送达，不受接收方开关限制
        if (toId == null || content == null) return;
        Message m = new Message();
        m.setConversationKey("SYS:" + toId);
        m.setScene(SCENE_SYSTEM);
        m.setFromId(0L);
        m.setToId(toId);
        m.setType("TEXT");
        m.setContent(content);
        messageMapper.insert(m);
    }

    @Override
    public void sendOfficial(Long toId, String content) {
        // 官方主动通知：强制送达，不受接收方「拒绝系统消息」开关限制
        if (toId == null || content == null || content.isBlank()) return;
        Message m = new Message();
        m.setConversationKey("SYS:" + toId);
        m.setScene(SCENE_SYSTEM);
        m.setFromId(0L);
        m.setToId(toId);
        m.setType("TEXT");
        m.setContent(content.trim());
        messageMapper.insert(m);
    }

    /** 发送方是否平台官方（超级管理员）：官方消息不受接收方的「拒绝私聊」开关限制 */
    private boolean isOfficialSender(Long uid) {
        if (uid == null) return false;
        User sender = userMapper.selectById(uid);
        return sender != null && ROLE_ADMIN.equals(sender.getRole());
    }

    /**
     * 客服会话归属：
     * - 商家回复时，自己的店就是会话所属店铺，接收人取前端传来的 toId；
     * - 普通用户咨询时，按 shopId 找到店铺，接收人固定为店主，防止冒充。
     */
    private Shop resolveServiceShop(Long uid, MessageSendDTO dto) {
        Shop own = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getOwnerId, uid).last("LIMIT 1"));
        if (own != null && dto.getToId() != null) {
            return own;
        }
        Long sid = dto.getShopId();
        if (sid == null) {
            // 商家没有传 shopId、也不是店主：尝试按 toId 反查对方是否是店主
            Shop peerShop = dto.getToId() == null ? null : shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                    .eq(Shop::getOwnerId, dto.getToId()).last("LIMIT 1"));
            if (peerShop == null) throw new BusinessException("请选择咨询的店铺");
            return peerShop;
        }
        Shop shop = shopMapper.selectById(sid);
        if (shop == null) throw new BusinessException("店铺不存在");
        if (shop.getOwnerId().equals(uid)) throw new BusinessException("不能给自己发消息");
        return shop;
    }

    private boolean canAccess(Long uid, Long peerId, String scene) {
        if (peerId == null || peerId.equals(uid)) return false;
        if (SCENE_FRIEND.equals(scene)) return friendService.isFriend(uid, peerId);
        // SERVICE：对方是店主，或自己是店主（自己店铺的客服会话）
        Long peerShop = shopMapper.selectCount(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getOwnerId, peerId));
        if (peerShop != null && peerShop > 0) return true;
        Long myShop = shopMapper.selectCount(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getOwnerId, uid));
        return myShop != null && myShop > 0;
    }

    private List<MessageVO> toVOs(List<Message> list, Long uid) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<Long> senderIds = list.stream().map(Message::getFromId)
                .filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Map<Long, User> users = users(senderIds);

        Set<Long> productIds = new HashSet<>();
        Set<Long> shopIds = new HashSet<>();
        Set<Long> reviewIds = new HashSet<>();
        for (Message m : list) {
            if (m.getTargetId() == null) continue;
            if ("PRODUCT".equals(m.getType())) productIds.add(m.getTargetId());
            else if ("SHOP".equals(m.getType())) shopIds.add(m.getTargetId());
            else if ("REVIEW".equals(m.getType())) reviewIds.add(m.getTargetId());
        }
        Map<Long, Product> products = productIds.isEmpty() ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Long, Shop> shops = shopIds.isEmpty() ? Collections.emptyMap()
                : shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s, (a, b) -> a));
        Map<Long, Review> reviews = reviewIds.isEmpty() ? Collections.emptyMap()
                : reviewMapper.selectBatchIds(reviewIds).stream()
                .collect(Collectors.toMap(Review::getId, r -> r, (a, b) -> a));
        Set<Long> reviewProductIds = reviews.values().stream().map(Review::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> reviewProducts = reviewProductIds.isEmpty() ? Collections.emptyMap()
                : productMapper.selectBatchIds(reviewProductIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        List<MessageVO> list2 = new ArrayList<>(list.size());
        for (Message m : list) {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId());
            vo.setScene(m.getScene());
            vo.setFromId(m.getFromId());
            vo.setToId(m.getToId());
            vo.setType(m.getType());
            vo.setContent(m.getContent());
            vo.setTargetId(m.getTargetId());
            vo.setShopId(m.getShopId());
            vo.setCreatedAt(m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
            vo.setTime(m.getCreatedAt() == null ? null : m.getCreatedAt().format(FMT));
            vo.setMine(m.getFromId().equals(uid));

            if (Long.valueOf(0).equals(m.getFromId())) {
                vo.setFromName("系统通知");
            } else {
                User u = users.get(m.getFromId());
                vo.setFromName(displayName(u, m.getFromId()));
                vo.setFromAvatar(u == null ? null : u.getAvatar());
            }

            if (m.getTargetId() != null) {
                if ("PRODUCT".equals(m.getType())) {
                    Product p = products.get(m.getTargetId());
                    if (p != null) {
                        vo.setTargetLinkId(p.getId());
                        vo.setTargetTitle(p.getName());
                        vo.setTargetSubtitle("¥" + p.getPrice());
                        vo.setTargetCover(p.getCover());
                    }
                } else if ("SHOP".equals(m.getType())) {
                    Shop s = shops.get(m.getTargetId());
                    if (s != null) {
                        vo.setTargetLinkId(s.getId());
                        vo.setTargetTitle(s.getName());
                        vo.setTargetSubtitle(s.getIntro());
                        vo.setTargetCover(s.getCover());
                    }
                } else if ("REVIEW".equals(m.getType())) {
                    Review r = reviews.get(m.getTargetId());
                    if (r != null) {
                        Product p = reviewProducts.get(r.getProductId());
                        vo.setTargetLinkId(r.getProductId());
                        vo.setTargetTitle(p == null ? "商品评价" : p.getName());
                        vo.setTargetSubtitle(r.getContent());
                        vo.setTargetCover(p == null ? null : p.getCover());
                    }
                }
            }
            list2.add(vo);
        }
        return list2;
    }

    private String preview(Message m) {
        if ("PRODUCT".equals(m.getType())) return "[商品分享]";
        if ("SHOP".equals(m.getType())) return "[店铺分享]";
        if ("REVIEW".equals(m.getType())) return "[评价分享]";
        String c = m.getContent() == null ? "" : m.getContent();
        return c.length() > 30 ? c.substring(0, 30) + "…" : c;
    }

    private Map<Long, User> users(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, Shop> shopsOf(Collection<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) return Collections.emptyMap();
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>().in(Shop::getOwnerId, ownerIds))
                .stream().collect(Collectors.toMap(Shop::getOwnerId, s -> s, (a, b) -> a));
    }

    private String displayName(User u, Long fallbackId) {
        if (u == null) return "用户 #" + fallbackId;
        return u.getNickname() == null || u.getNickname().isEmpty() ? u.getUsername() : u.getNickname();
    }

    static String convKey(Long a, Long b, String scene) {
        long x = Math.min(a, b);
        long y = Math.max(a, b);
        return x + ":" + y + ":" + scene;
    }
}

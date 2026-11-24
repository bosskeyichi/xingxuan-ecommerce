package com.ecommerce.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Session store backed by in-memory ConcurrentHashMap.
 * Replaces Redis for a zero-install / zero-config runtime.
 * <p>
 * 单设备登录的粒度是「账号 + 登录端」：同一账号在用户端（USER）与后台（ADMIN）
 * 各自保留一个会话，所以管理员可以在前台浏览商品的同时留在后台操作；
 * 同一端重复登录仍会挤掉旧会话，单设备登录约束始终生效。
 */
@Component
public class SessionStore {

    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_ADMIN = "ADMIN";

    private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

    /** 保存当前用户会话：key = "user:session:{userId}:{scope}", value = token */
    public void put(String key, String token) {
        sessions.put(key, token);
    }

    public String get(String key) {
        return sessions.get(key);
    }

    public void remove(String key) {
        sessions.remove(key);
    }

    public boolean containsKey(String key) {
        return sessions.containsKey(key);
    }

    /** 归一化登录端：只区分用户端与后台，其余一律按用户端处理 */
    public static String normalize(String scope) {
        return SCOPE_ADMIN.equalsIgnoreCase(scope) ? SCOPE_ADMIN : SCOPE_USER;
    }

    public static String key(Long userId, String scope) {
        return "user:session:" + userId + ":" + normalize(scope);
    }

    public static String key(Long userId) {
        return key(userId, SCOPE_USER);
    }

    /** 禁用 / 删除 / 改密 / 重置数据时，把该账号两端会话一起清掉 */
    public void removeAll(Long userId) {
        String prefix = "user:session:" + userId + ":";
        sessions.keySet().removeIf(k -> k.startsWith(prefix));
    }
}

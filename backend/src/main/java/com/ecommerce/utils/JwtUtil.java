package com.ecommerce.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 token：scope 区分用户端（USER）与后台（ADMIN），两端互不挤占会话 */
    public String generate(Long userId, String username, String role, String scope) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("scope", SessionStore.normalize(scope));
        Date now = new Date();
        Date expire = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(username)
                // jti 保证每次登录的 token 都不同（同一秒内重复登录也能互斥）
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expire)
                .signWith(key)
                .compact();
    }

    /** 解析 token */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT 解析失败：{}", e.getMessage());
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims c = parse(token);
        return c == null ? null : c.get("userId", Long.class);
    }

    public String getUsername(String token) {
        Claims c = parse(token);
        return c == null ? null : c.get("username", String.class);
    }

    public String getRole(String token) {
        Claims c = parse(token);
        return c == null ? null : c.get("role", String.class);
    }

    /** 基于已解析的 Claims 取 userId（别让重复验签） */
    public Long getUserId(Claims c) {
        return c == null ? null : c.get("userId", Long.class);
    }

    /** 基于已解析的 Claims 取 role */
    public String getRole(Claims c) {
        return c == null ? null : c.get("role", String.class);
    }

    /** 基于已解析的 Claims 取 username */
    public String getUsername(Claims c) {
        return c == null ? null : c.get("username", String.class);
    }

    /** 基于已解析的 Claims 取登录端；旧 token 没有该字段时按用户端处理 */
    public String getScope(Claims c) {
        Object v = c == null ? null : c.get("scope");
        return v == null ? SessionStore.SCOPE_USER : SessionStore.normalize(v.toString());
    }
}
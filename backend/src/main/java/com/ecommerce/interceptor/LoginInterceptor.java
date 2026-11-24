package com.ecommerce.interceptor;

import com.ecommerce.common.Result;
import com.ecommerce.utils.JwtUtil;
import com.ecommerce.utils.SessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Login interceptor: validate JWT + enforce single-device login
 * (session stored in SessionStore, an in-memory replacement for Redis).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;

    @Value("${jwt.header}")
    private String headerName;

    @Value("${jwt.prefix}")
    private String prefix;

    /** ThreadLocal context for the current request's user */
    public static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    public static final ThreadLocal<String> ROLE  = new ThreadLocal<>();
    /** 当前请求的登录端（USER / ADMIN），退出登录时用它清理对应会话 */
    public static final ThreadLocal<String> SCOPE = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader(headerName);
        if (token == null || token.isEmpty()) {
            write(response, 401, Result.error(401, "未登录或登录已失效"));
            return false;
        }
        if (token.startsWith(prefix)) {
            token = token.substring(prefix.length());
        }

        // 一次解析，同时取出 userId / role，别让重复验签
        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            write(response, 401, Result.error(401, "登录已过期，请重新登录"));
            return false;
        }
        Long userId = jwtUtil.getUserId(claims);
        String role = jwtUtil.getRole(claims);
        if (userId == null) {
            write(response, 401, Result.error(401, "登录已过期，请重新登录"));
            return false;
        }

        // 单设备登录：按「账号 + 登录端」比对，前台与后台各自独立，同端重复登录才会挤下线
        String scope = jwtUtil.getScope(claims);
        String sessionKey = SessionStore.key(userId, scope);
        String saved = sessionStore.get(sessionKey);
        if (saved == null) {
            write(response, 401, Result.error(401, "登录状态已失效，请重新登录"));
            return false;
        }
        if (!saved.equals(token)) {
            write(response, 401, Result.error(401, "账号已在其他设备/窗口登录，你已被下线"));
            return false;
        }

        USER_ID.set(userId);
        ROLE.set(role);
        SCOPE.set(scope);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        USER_ID.remove();
        ROLE.remove();
        SCOPE.remove();
    }

    private void write(HttpServletResponse response, int httpStatus, Result<?> r) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(r));
    }
}

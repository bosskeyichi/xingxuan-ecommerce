package com.ecommerce.interceptor;

import com.ecommerce.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台权限拦截器：必须注册在 LoginInterceptor 之后，
 * 依赖 LoginInterceptor 写入的 ROLE ThreadLocal 判断角色。
 * <p>
 * 多商家场景下，ADMIN（平台管理员）与 MERCHANT（商家）都可进入后台，
 * 但各自的数据范围由 Service 层按 shopId 限定；平台级功能（用户 / 分类 / 优惠券 / 店铺管理）
 * 由 PlatformInterceptor 再做一层限制。
 */
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String role = LoginInterceptor.ROLE.get();
        if (!"ADMIN".equals(role) && !"MERCHANT".equals(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.error(403, "无权限操作，仅管理员与商家可访问")));
            return false;
        }
        return true;
    }
}

package com.ecommerce.interceptor;

import com.ecommerce.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 平台级功能拦截器：用户管理、分类、优惠券、店铺管理等属于平台运营，
 * 商家（MERCHANT）即使能进后台也不能操作这些接口。
 */
@Component
@RequiredArgsConstructor
public class PlatformInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!"ADMIN".equals(LoginInterceptor.ROLE.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.error(403, "该功能仅平台管理员可操作")));
            return false;
        }
        return true;
    }
}

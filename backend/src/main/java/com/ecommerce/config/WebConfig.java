package com.ecommerce.config;

import com.ecommerce.interceptor.AdminInterceptor;
import com.ecommerce.interceptor.LoginInterceptor;
import com.ecommerce.interceptor.PlatformInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：跨域 + 拦截器
 * <p>
 * 拦截器顺序：LoginInterceptor（认证） → AdminInterceptor（后台身份：管理员 / 商家） → PlatformInterceptor（平台级功能）
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final AdminInterceptor adminInterceptor;
    private final PlatformInterceptor platformInterceptor;

    /** 无需登录即可访问的路径 */
    private static final String[] PUBLIC_PATHS = {
            "/health",
            "/auth/login",
            "/auth/register",
            "/product/list",
            "/product/detail/**",
            "/product/search",
            "/product/hot",
            "/product/recommend",
            "/review/list/**",
            "/review/summary/**",
            "/category/list",
            "/coupon/available",
            "/shop/list",
            "/shop/search",
            "/shop/detail/**",
            "/error",
            "/doc.html",
            "/v3/api-docs/**"
    };

    /** 只有管理员能进的路径（在登录校验通过后再校验角色） */
    private static final String[] ADMIN_PATHS = {
            "/product/admin/**",
            "/category/admin/**",
            "/order/admin/**",
            "/review/admin/**",
            "/coupon/admin/**",
            "/auth/admin/**",
            "/shop/admin/**",
            "/report/admin/**",
            "/admin/**"
    };

    /**
     * 平台级功能：商家不可访问（用户 / 分类 / 评价治理 / 店铺运营）。
     * 注意：优惠券对商家开放（商家可发本店券），由 CouponServiceImpl 的 scopeShopId 限定数据范围，
     * 所以这里不包含 /coupon/admin/**。
     */
    private static final String[] PLATFORM_PATHS = {
            "/auth/admin/**",
            "/category/admin/**",
            "/review/admin/**",
            "/shop/admin/list",
            "/shop/admin/*/status"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 登录校验
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_PATHS);

        // 2. 后台身份校验：管理员或商家（注册顺序 = 跑顺序，必须在 loginInterceptor 之后）
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns(ADMIN_PATHS);

        // 3. 平台级功能校验：仅管理员
        registry.addInterceptor(platformInterceptor)
                .addPathPatterns(PLATFORM_PATHS);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

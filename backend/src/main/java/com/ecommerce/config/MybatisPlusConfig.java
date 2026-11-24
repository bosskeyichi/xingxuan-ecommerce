package com.ecommerce.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数，防止 size 过大导致 OOM */
    @Value("${page.max-size:100}")
    private long maxSize;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor i = new MybatisPlusInterceptor();
        PaginationInnerInterceptor page = new PaginationInnerInterceptor(DbType.MYSQL);
        page.setMaxLimit(maxSize);
        // 超过最大页数时回到首页，别让查询空页
        page.setOverflow(false);
        i.addInnerInterceptor(page);
        return i;
    }
}

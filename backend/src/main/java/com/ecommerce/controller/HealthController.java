package com.ecommerce.controller;

import com.ecommerce.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：供 Docker / Nginx / 运维探活使用，无需登录
 * <p>
 * 返回服务名、实例标识与当前时间，便于在分布式部署中确认请求实际落在哪个实例上。
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @Value("${spring.application.name:ecommerce-backend}")
    private String appName;

    @Value("${server.port:8080}")
    private String port;

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", appName);
        data.put("instance", System.getenv().getOrDefault("HOSTNAME", "local"));
        data.put("port", port);
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.success("服务正常", data);
    }
}

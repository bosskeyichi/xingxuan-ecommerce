package com.ecommerce;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电商系统启动类
 */
@EnableScheduling
@SpringBootApplication
@MapperScan("com.ecommerce.mapper")
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  电商系统启动成功");
        System.out.println("  API 地址: http://localhost:8080/api");
        System.out.println("========================================\n");
    }
}

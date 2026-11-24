package com.ecommerce.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具（BCrypt）
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {}

    public static String encode(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String hashed) {
        try {
            return ENCODER.matches(raw, hashed);
        } catch (Exception e) {
            return false;
        }
    }
}
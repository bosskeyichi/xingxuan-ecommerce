package com.ecommerce.exception;

import lombok.Getter;

/**
 * 业务异常（默认 400，表示客户端/业务校验失败）
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}

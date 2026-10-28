package com.campushub.common;

import lombok.Getter;

/**
 * 业务异常类.
 * 当业务规则被违反时，Service 层抛出此异常，由 GlobalExceptionHandler 统一拦截。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400; // 默认为客户端业务请求错误
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
package com.lostfound.common;

import lombok.Getter;

/**
 * 业务异常 — 在 service 层抛出，由 GlobalExceptionHandler 统一捕获并转为 Result 返回。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_ERROR;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.INTERNAL_ERROR;
    }
}

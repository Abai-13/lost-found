package com.lostfound.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 统一返回结果封装。
 * <p>
 * 所有 Controller 都返回此类型，前端通过 code 判断成功/失败。
 *
 * @param <T> 数据类型
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;
    private final long timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ===================== 成功 =====================

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS, ResultCode.SUCCESS_MSG, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS, ResultCode.SUCCESS_MSG, data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ResultCode.SUCCESS, message, data);
    }

    // ===================== 失败 =====================

    public static <T> Result<T> fail() {
        return new Result<>(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR_MSG, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(ResultCode.INTERNAL_ERROR, message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ===================== 快捷方法 =====================

    public static <T> Result<T> unauthorized() {
        return new Result<>(ResultCode.UNAUTHORIZED, ResultCode.UNAUTHORIZED_MSG, null);
    }

    public static <T> Result<T> forbidden() {
        return new Result<>(ResultCode.FORBIDDEN, ResultCode.FORBIDDEN_MSG, null);
    }

    public static <T> Result<T> notFound() {
        return new Result<>(ResultCode.NOT_FOUND, ResultCode.NOT_FOUND_MSG, null);
    }

    public static <T> Result<T> badRequest(String message) {
        return new Result<>(ResultCode.BAD_REQUEST, message != null ? message : ResultCode.BAD_REQUEST_MSG, null);
    }
}

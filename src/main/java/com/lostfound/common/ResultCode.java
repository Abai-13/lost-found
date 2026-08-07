package com.lostfound.common;

/**
 * 统一返回结果状态码
 */
public final class ResultCode {

    private ResultCode() {}

    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int INTERNAL_ERROR = 500;

    public static final String SUCCESS_MSG = "操作成功";
    public static final String BAD_REQUEST_MSG = "请求参数错误";
    public static final String UNAUTHORIZED_MSG = "未登录或 token 已过期";
    public static final String FORBIDDEN_MSG = "无权限访问";
    public static final String NOT_FOUND_MSG = "资源不存在";
    public static final String INTERNAL_ERROR_MSG = "服务器内部错误";
}

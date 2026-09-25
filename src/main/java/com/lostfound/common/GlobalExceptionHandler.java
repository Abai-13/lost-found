package com.lostfound.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * Controller 抛出异常不用手动 try-catch，这里统一拦截并返回 Result。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 — 直接返回给前端 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    /**
     * 请求体读不出来 —— 不是合法 JSON，或者编码不是 UTF-8。
     * <p>
     * <b>为什么必须单独接住这个异常</b>：不接的话它会掉进最下面的兜底，
     * 客户端的问题被报成 {@code 500 服务器内部错误}。
     * <p>
     * 实测踩到过：用 curl 发一条带中文昵称的 JSON 测注册接口，接口返回 500。
     * 根因是 <b>curl 在中文 Windows 上会把命令行参数按系统代码页转成字节</b>
     * （本机代码页 936 = GBK），中文被发成 GBK 字节，Jackson 按 UTF-8 解析就抛
     * {@code JsonParseException: Invalid UTF-8 middle byte 0xeb}。
     * <p>
     * <b>归因要准确：跟 Git Bash 无关</b> —— 换任何终端都一样，是 curl 在 Windows 上的行为。
     * 对照实验（同一段 JSON，只改中文写在哪）：
     * <ul>
     *   <li>{@code -d '{"nickname":"白晨鑫"}'} → 服务端收到 {@code b0 d7 b3 bf f6 ce}（GBK）→ 500</li>
     *   <li>{@code --data-binary @body-utf8.json} → 服务端收到 {@code e7 99 bd ...}（UTF-8）→ 200</li>
     * </ul>
     * <p>
     * 当时排查方向整个跑偏 —— 去查了数据库表结构、怀疑后端进程跑的是旧代码，
     * 唯独没想到「是我自己发的数据有问题」。因为返回的是 500，看上去就是服务端的锅。
     * <p>
     * 这和 Redis 那个 bug 是同一类：<b>错误被归到了错误的层级</b>，
     * 表面现象（500）和真实原因（客户端数据非法）差着十万八千里。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析（多半是 JSON 格式错或不是 UTF-8 编码）: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "请求体格式不正确：不是合法的 JSON，或编码不是 UTF-8");
    }

    /** 路径参数/请求参数校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    /** 兜底 — 所有未处理的异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail();
    }
}

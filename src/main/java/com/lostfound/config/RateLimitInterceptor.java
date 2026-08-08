package com.lostfound.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单限流拦截器 — 基于内存计数器。
 * <p>
 * 每个用户每分钟最多 5 次请求。
 * 如果未来需要分布式部署，可将 ConcurrentHashMap 替换为 Redis incr + expire。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 每个用户每分钟最大请求数 */
    private static final int MAX_REQUESTS_PER_MINUTE = 5;

    /** 限流窗口（毫秒） */
    private static final long WINDOW_MS = 60_000;

    /** userId → 请求时间列表 */
    private final ConcurrentHashMap<Long, List<Long>> requestLog = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 从 JWT 拦截器设置的 attribute 中取出 userId
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            // 理论上不会发生（JWT 拦截器已校验），兜底放行
            return true;
        }

        Long userId = (Long) userIdAttr;
        long now = System.currentTimeMillis();

        // 原子操作：获取或创建该用户的记录列表
        List<Long> timestamps = requestLog.computeIfAbsent(userId, k -> new ArrayList<>());

        synchronized (timestamps) {
            // ① 清理超过 1 分钟的旧记录
            timestamps.removeIf(t -> now - t > WINDOW_MS);

            // ② 判断是否超过限制
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                log.warn("用户 {} 触发限流，1 分钟内请求 {} 次", userId, timestamps.size());
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(429); // Too Many Requests
                response.getWriter().write(
                        "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null,\"timestamp\":" + now + "}"
                );
                return false;
            }

            // ③ 记录本次请求时间
            timestamps.add(now);
        }

        return true;
    }
}

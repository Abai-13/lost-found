package com.lostfound.interceptor;

import com.lostfound.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器。
 * <p>
 * GET 请求（查询类）允许无 token 访问（公开浏览），
 * POST/PUT/DELETE（写操作）必须带有效 token。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    private static final String USER_ID_KEY = "userId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean hasToken = StringUtils.hasText(header) && header.startsWith("Bearer ");

        // GET 请求：有 token 就解析用户信息，没有也放行（公开浏览）
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            if (hasToken) {
                String token = header.substring(7);
                if (jwtUtil.validate(token)) {
                    request.setAttribute(USER_ID_KEY, jwtUtil.getUserId(token));
                }
            }
            return true;
        }

        // 写操作（POST/PUT/DELETE）：必须有有效 token
        if (!hasToken) {
            response.setStatus(401);
            return false;
        }

        String token = header.substring(7);
        if (!jwtUtil.validate(token)) {
            response.setStatus(401);
            return false;
        }

        request.setAttribute(USER_ID_KEY, jwtUtil.getUserId(token));
        return true;
    }
}

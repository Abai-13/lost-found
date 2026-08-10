package com.lostfound.config;

import com.lostfound.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置 — CORS + 拦截器注册。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    /** CORS — 开发阶段允许所有来源 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * 静态资源映射 — 让浏览器能访问本地磁盘上的上传文件。
     * /uploads/xxx.jpg → 磁盘上的 uploads/xxx.jpg
     * "file:" 前缀表示这是磁盘路径，不是 classpath。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    /** 注册 JWT 拦截器 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                // 放行不需要登录的接口
                .excludePathPatterns(
                        "/api/user/register",
                        "/api/user/login"
                );

        // 限流拦截器 — 仅拦截 AI 接口（在 JWT 之后执行）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/ai/**");
    }
}

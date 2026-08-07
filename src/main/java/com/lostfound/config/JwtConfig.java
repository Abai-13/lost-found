package com.lostfound.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置 — 从 application.yml 读取。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    /** 签名密钥（至少 256 位） */
    private String secret;
    /** 过期天数 */
    private int expireDays = 7;
}

package com.lostfound.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类（Spring Bean，密钥从配置文件注入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtConfig jwtConfig;

    /** 获取签名密钥 */
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 创建 token */
    public String createToken(Long userId, Map<String, Object> claims) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + jwtConfig.getExpireDays() * 24L * 60 * 60 * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(now)
                .expiration(expire)
                .signWith(getKey())
                .compact();
    }

    /** 解析 token */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 token 获取 userId */
    public Long getUserId(String token) {
        String subject = parseToken(token).getSubject();
        return Long.valueOf(subject);
    }

    /** 校验 token 是否有效 */
    public boolean validate(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("JWT 无效: {}", e.getMessage());
            return false;
        }
    }
}

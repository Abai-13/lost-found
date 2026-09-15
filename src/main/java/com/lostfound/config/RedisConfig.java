package com.lostfound.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存配置。
 * <p>
 * 启用 Spring Cache 注解（@Cacheable / @CacheEvict / @CachePut），
 * 并配置 JSON 序列化（存人类可读的 JSON 字符串，而不是 Java 二进制）。
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    /**
     * 专门给 Redis 用的序列化器。
     * <p>
     * 为什么不用默认的 {@code new GenericJackson2JsonRedisSerializer()}：
     * 它内部的 ObjectMapper 没有注册 JavaTimeModule，遇到 {@code LocalDateTime}
     * （Item 的 createdAt / updatedAt）会直接抛
     * "Java 8 date/time type not supported by default"。
     * 这个异常会被 {@link GracefulCacheErrorHandler} 吞掉 —— 表面正常，但缓存永远写不进去。
     * <p>
     * 为什么不用 Spring MVC 那个 ObjectMapper：它是给接口返回 JSON 用的，
     * 开 default typing 会把 {@code @class} 字段混进接口响应里。
     */
    private GenericJackson2JsonRedisSerializer redisSerializer() {
        ObjectMapper mapper = new ObjectMapper();

        // ① 关键修复：注册 Java 8 时间模块，让 LocalDateTime 能序列化
        mapper.registerModule(new JavaTimeModule());
        // 时间写成 "2026-09-12T12:00:00" 而不是一串数字时间戳，方便在 redis-cli 里看
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ② 写入类型信息（@class），反序列化时才还原得回 Page / Item
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * 缓存管理器 — 管理缓存的创建、读写、过期。
     * 所有 @Cacheable 注解最终都走这里。
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // 默认过期时间：5 分钟
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                // 用 JSON 序列化值（看得懂、好调试）
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                redisSerializer()));
        // 注：不再禁用 null 值缓存 —— 让 null 也被缓存（防缓存穿透）

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .build();
    }

    /**
     * RedisTemplate — Spring 操作 Redis 的核心工具类。
     * 如果不用 Spring Cache 注解，可以手动用 template 读写 Redis。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 用字符串序列化（方便在 redis-cli 里查看）
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 用 JSON 序列化（兼容不同类型）
        GenericJackson2JsonRedisSerializer serializer = redisSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 缓存降级：Redis 不可用时，缓存读写失败不再抛异常，而是记录日志后静默降级。
     * - 读失败 → 当作缓存未命中，直接查数据库
     * - 写/删失败 → 忽略，数据仍以数据库为准
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new GracefulCacheErrorHandler();
    }
}

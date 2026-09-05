package com.lostfound.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;

/**
 * 缓存降级处理器。
 * <p>
 * Redis 不可用时，缓存读写失败不再抛异常，而是记录日志后静默降级：
 * - 读失败 → 当作缓存未命中，直接查数据库
 * - 写/删失败 → 忽略，数据仍以数据库为准
 * <p>
 * 目的：缓存故障（Redis 宕机、网络抖动）不会拖垮主流程，服务照常可用。
 */
@Slf4j
public class GracefulCacheErrorHandler extends SimpleCacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("缓存读取失败，降级为直查数据库: cache={}, key={}, err={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("缓存写入失败，忽略: cache={}, key={}, err={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("缓存删除失败，忽略: cache={}, key={}, err={}",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("缓存清空失败，忽略: cache={}, err={}",
                cache.getName(), exception.getMessage());
    }
}

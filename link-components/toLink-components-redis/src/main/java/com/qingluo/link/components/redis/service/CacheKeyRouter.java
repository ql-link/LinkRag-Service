package com.qingluo.link.components.redis.service;

import org.springframework.stereotype.Component;

/**
 * 缓存 key 路由器。
 *
 * <p>负责把逻辑缓存目标转换成实际 Redis key，保证主请求同步删缓存与 CDC
 * 异步补偿删缓存使用同一套路由口径。</p>
 */
@Component
public class CacheKeyRouter {

    public CacheRoute route(CacheEvictTarget target, String identifier) {
        if (target == null) {
            throw new IllegalArgumentException("Cache target is required");
        }
        return switch (target) {
            case DATASET_PARSE_CONFIG -> CacheRoute.of("cache:dataset:parse-config:" + required(identifier));
            case USER_PROFILE -> CacheRoute.of("cache:user:profile:" + required(identifier));
            case PUBLISHED_BLOG_INDEX -> CacheRoute.of("cache:blog:published-index");
        };
    }

    private String required(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Cache route identifier is required");
        }
        return identifier;
    }
}

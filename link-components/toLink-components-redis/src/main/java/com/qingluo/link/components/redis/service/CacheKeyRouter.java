package com.qingluo.link.components.redis.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存 key 路由器。
 *
 * <p>负责把逻辑缓存目标转换成实际 Redis key，保证主请求同步删缓存与 CDC
 * 异步补偿删缓存使用同一套路由口径。</p>
 */
@Component
public class CacheKeyRouter {

    public List<String> route(CacheEvictTarget target, String identifier) {
        return List.of();
    }
}

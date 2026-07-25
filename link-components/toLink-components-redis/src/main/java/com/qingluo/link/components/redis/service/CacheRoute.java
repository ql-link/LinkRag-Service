package com.qingluo.link.components.redis.service;

/**
 * 一份数据库镜像缓存的三个 Redis key。
 */
public record CacheRoute(String dataKey, String fenceKey, String lockKey) {

    public static CacheRoute of(String dataKey) {
        String suffix = dataKey.startsWith("cache:") ? dataKey.substring("cache:".length()) : dataKey;
        return new CacheRoute(
            dataKey,
            "cache:fence:" + suffix,
            "cache:lock:" + suffix
        );
    }
}

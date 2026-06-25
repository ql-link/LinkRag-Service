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
        return switch (target) {
            case USER -> List.of(userInfoKey(identifier), userRoleKey(identifier));
            case USER_INFO -> List.of(userInfoKey(identifier));
            case USER_ROLE -> List.of(userRoleKey(identifier));
            case LLM_CONFIG -> List.of("llm:cfg:" + identifier);
            case USER_DEFAULT_LLM_CONFIG -> List.of("llm:u_def:" + identifier, "llm:u_cfg:" + identifier);
            // 除按厂商类型删单厂商 key 外，连带删该厂商的模型分片与厂商索引，
            // 使该厂商模型变更或厂商增减都让用户侧目录缓存失效
            // （分片/索引 key 对应 ProviderCatalogCacheService.MODELS_KEY_PREFIX / INDEX_KEY）。
            case SYSTEM_PROVIDER -> List.of("llm:pvd:" + identifier,
                    "llm:pvd:catalog:models:" + identifier,
                    "llm:pvd:catalog:index");
        };
    }

    private String userInfoKey(String userId) {
        return "user:info:" + userId;
    }

    private String userRoleKey(String userId) {
        return "user:role:" + userId;
    }
}

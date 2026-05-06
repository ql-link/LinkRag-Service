package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.entity.UserLLMConfig;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 用户 LLM 配置缓存 owner service。
 *
 * <p>统一封装用户配置详情 key 与用户默认配置映射 key，业务服务不直接拼接 Redis key。</p>
 */
public interface UserLLMConfigCacheService {

    UserLLMConfig getConfigOrLoad(Long configId, Supplier<UserLLMConfig> loader);

    Map<String, Long> getDefaultConfigIdMapOrLoad(Long userId, Supplier<Map<String, Long>> loader);

    void evictConfig(Long configId);

    void evictDefaultMap(Long userId);
}

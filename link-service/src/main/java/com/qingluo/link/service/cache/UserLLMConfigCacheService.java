package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.response.UserLLMConfigDTO;

import java.util.List;
import java.util.function.Supplier;

/**
 * 用户 LLM 配置读缓存。
 *
 * <p>按用户缓存脱敏后的配置 DTO 全量列表，调用方在内存中做 provider/capability/active/default 过滤。</p>
 */
public interface UserLLMConfigCacheService {

    /**
     * 读取某用户全部 LLM 配置；缓存未命中时由 loader 回源数据库。
     */
    List<UserLLMConfigDTO> getOrLoadAll(Long userId, Supplier<List<UserLLMConfigDTO>> loader);
}

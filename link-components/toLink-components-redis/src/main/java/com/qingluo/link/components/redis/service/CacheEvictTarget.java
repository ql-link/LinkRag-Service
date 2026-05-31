package com.qingluo.link.components.redis.service;

import java.util.Arrays;

/**
 * 统一缓存驱逐目标枚举。
 *
 * <p>用于抽象“删哪一类缓存”，避免业务代码和补偿消费者直接散落硬编码 Redis key 前缀。</p>
 */
public enum CacheEvictTarget {

    USER("user"),
    USER_INFO("user_info"),
    USER_ROLE("user_role"),
    LLM_CONFIG("llm_config"),
    USER_DEFAULT_LLM_CONFIG("user_default_llm_config"),
    SYSTEM_PROVIDER("system_provider");

    private final String code;

    CacheEvictTarget(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CacheEvictTarget fromCode(String code) {
        return Arrays.stream(values())
                .filter(target -> target.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cache target: " + code));
    }
}

package com.qingluo.link.components.redis.service;

import java.util.Arrays;

/**
 * 统一缓存驱逐目标枚举。
 *
 * <p>用于抽象“删哪一类缓存”，避免业务代码和补偿消费者直接散落硬编码 Redis key 前缀。</p>
 */
public enum CacheEvictTarget {
    DATASET_PARSE_CONFIG("dataset_parse_config"),
    USER_PROFILE("user_profile"),
    PUBLISHED_BLOG_INDEX("published_blog_index");

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

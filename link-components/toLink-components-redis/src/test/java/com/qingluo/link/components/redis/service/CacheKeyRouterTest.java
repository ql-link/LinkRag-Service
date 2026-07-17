package com.qingluo.link.components.redis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CacheKeyRouterTest {

    private final CacheKeyRouter router = new CacheKeyRouter();

    @Test
    void routesUseStableNamesWithoutArtificialVersionSegments() {
        assertThat(router.route(CacheEvictTarget.DATASET_PARSE_CONFIG, "10").dataKey())
            .isEqualTo("cache:dataset:parse-config:10")
            .doesNotContain("v1", "v2");
        assertThat(router.route(CacheEvictTarget.USER_PROFILE, "20").dataKey())
            .isEqualTo("cache:user:profile:20");
        assertThat(router.route(CacheEvictTarget.PUBLISHED_BLOG_INDEX, "global").dataKey())
            .isEqualTo("cache:blog:published-index");
    }

    @Test
    void retiredTargetCodeIsRejected() {
        assertThatThrownBy(() -> CacheEvictTarget.fromCode("user"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

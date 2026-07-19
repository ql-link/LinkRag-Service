package com.qingluo.link.components.redis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CacheKeyRouterTest {

    private final CacheKeyRouter router = new CacheKeyRouter();

    @Test
    void routesUseStableNamesWithoutArtificialVersionSegments() {
        assertThat(router.route(CacheEvictTarget.DATASET_PARSE_CONFIG, "10").dataKey())
            .isEqualTo("cache:dataset:parse-config:{dataset-config:10}")
            .doesNotContain("v1", "v2");
        assertThat(router.route(CacheEvictTarget.USER_PROFILE, "20").dataKey())
            .isEqualTo("cache:user:profile:20");
        assertThat(router.route(CacheEvictTarget.PUBLISHED_BLOG_INDEX, "global").dataKey())
            .isEqualTo("cache:blog:published-index");
    }

    @Test
    void datasetParseConfigRoute_usesSharedRedisClusterHashTagForAllThreeKeys() {
        CacheRoute route = router.route(CacheEvictTarget.DATASET_PARSE_CONFIG, "10");

        assertThat(route.dataKey())
            .isEqualTo("cache:dataset:parse-config:{dataset-config:10}");
        assertThat(route.fenceKey())
            .isEqualTo("cache:fence:dataset:parse-config:{dataset-config:10}");
        assertThat(route.lockKey())
            .isEqualTo("cache:lock:dataset:parse-config:{dataset-config:10}");
    }

    @Test
    void retiredTargetCodeIsRejected() {
        assertThatThrownBy(() -> CacheEvictTarget.fromCode("user"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void llmRuntimeRoute_usesSharedRedisClusterHashTagForAllThreeKeys() {
        CacheRoute route = router.route(CacheEvictTarget.LLM_RUNTIME_CONFIG, "10001");

        assertThat(CacheEvictTarget.fromCode("llm_runtime_config"))
            .isEqualTo(CacheEvictTarget.LLM_RUNTIME_CONFIG);
        assertThat(route.dataKey())
            .isEqualTo("cache:llm:runtime-config:{llm-runtime:10001}");
        assertThat(route.fenceKey())
            .isEqualTo("cache:fence:llm:runtime-config:{llm-runtime:10001}");
        assertThat(route.lockKey())
            .isEqualTo("cache:lock:llm:runtime-config:{llm-runtime:10001}");
    }
}

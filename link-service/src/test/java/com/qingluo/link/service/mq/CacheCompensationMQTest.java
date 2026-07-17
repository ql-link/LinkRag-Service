package com.qingluo.link.service.mq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheCompensationMQTest {

    @Test
    void Should_AcceptLlmRuntimeConfigTarget() {
        CacheCompensationMQ mq = new CacheCompensationMQ(new CacheCompensationMQ.MsgPayload(
            "evt-llm-1",
            "llm_runtime_config",
            "10001",
            "llm_model_config",
            "UPDATE",
            "trace-1",
            "2026-07-17T18:00:00+08:00"
        ));

        assertThat(CacheCompensationMQ.parseMsg(mq.getMessage()).parseTarget())
            .isEqualTo(com.qingluo.link.components.redis.service.CacheEvictTarget.LLM_RUNTIME_CONFIG);
    }

    @Test
    @DisplayName("Should_RejectHistoricalBusinessTarget_When_NoActiveCacheTargets")
    void Should_RejectHistoricalBusinessTarget_When_NoActiveCacheTargets() {
        CacheCompensationMQ mq = new CacheCompensationMQ(new CacheCompensationMQ.MsgPayload(
                "evt-1",
                "user",
                "1001",
                "sys_user",
                "UPDATE",
                "trace-1",
                "2026-05-06T12:00:00+08:00"
        ));

        assertThat(mq.getMQName()).isEqualTo("tolink.cache.evict");
        assertThatThrownBy(mq::getMessage)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown cache target: user");
    }

    @Test
    @DisplayName("Should_RejectMessage_When_TargetInvalid")
    void Should_RejectMessage_When_TargetInvalid() {
        CacheCompensationMQ mq = new CacheCompensationMQ(new CacheCompensationMQ.MsgPayload(
                "evt-1",
                "unknown",
                "1001",
                "sys_user",
                "UPDATE",
                null,
                "2026-05-06T12:00:00+08:00"
        ));

        assertThatThrownBy(mq::getMessage)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown cache target: unknown");
    }
}

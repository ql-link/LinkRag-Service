package com.qingluo.link.service.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheCompensationMQTest {

    @Test
    @DisplayName("Should_SerializeFlatJson_When_BuildCacheCompensationMessage")
    void Should_SerializeFlatJson_When_BuildCacheCompensationMessage() {
        CacheCompensationMQ mq = new CacheCompensationMQ(new CacheCompensationMQ.MsgPayload(
                "evt-1",
                "user",
                "1001",
                "sys_user",
                "UPDATE",
                "trace-1",
                "2026-05-06T12:00:00+08:00"
        ));

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(mq.getMQName()).isEqualTo("tolink.cache.evict");
        assertThat(json.getString("event_id")).isEqualTo("evt-1");
        assertThat(json.getString("cache_target")).isEqualTo("user");
        assertThat(json.getString("route_id")).isEqualTo("1001");
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

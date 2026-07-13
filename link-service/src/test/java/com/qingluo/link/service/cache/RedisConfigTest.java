package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.RedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    @DisplayName("Redis value serializer 基础能力支持 LocalDateTime")
    void redisValueSerializer_supportsLocalDateTime() {
        RedisSerializer<Object> serializer = createValueSerializer();
        byte[] bytes = serializer.serialize(Map.of("createdAt", LocalDateTime.of(2026, 7, 10, 12, 0)));

        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("createdAt", "2026-07-10T12:00:00");
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<Object> createValueSerializer() {
        return (RedisSerializer<Object>) new RedisConfig()
                .redisTemplate(mock(RedisConnectionFactory.class))
                .getValueSerializer();
    }
}

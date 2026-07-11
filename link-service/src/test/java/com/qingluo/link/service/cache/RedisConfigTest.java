package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.RedisConfig;
import com.qingluo.link.model.dto.entity.ProviderModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    @DisplayName("Redis value serializer 支持包含 LocalDateTime 的模型分片")
    void redisValueSerializer_supportsProviderModelShardWithLocalDateTime() {
        RedisSerializer<Object> serializer = createValueSerializer();
        ProviderModel model = new ProviderModel();
        model.setId(1L);
        model.setProviderId(10L);
        model.setModelName("mimo-chat");
        model.setCapability("CHAT");
        model.setCreatedAt(LocalDateTime.of(2026, 7, 10, 12, 0));
        model.setUpdatedAt(LocalDateTime.of(2026, 7, 10, 12, 30));
        ProviderModelShard shard = new ProviderModelShard(List.of(model));

        byte[] bytes = serializer.serialize(shard);

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

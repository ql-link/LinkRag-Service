package com.qingluo.link.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.components.redis.RedisConfig;
import com.qingluo.link.model.dto.cache.DatasetParseConfigCacheEnvelope;
import com.qingluo.link.model.dto.cache.DatasetParseConfigSnapshot;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

class DatasetParseConfigCacheContractTest {

    @Test
    void redisJson_containsOnlyRawDatabaseProjectionWithoutJavaDefaults() throws Exception {
        RecallConfig recall = new RecallConfig();
        recall.setDenseTopK(5);
        DatasetParseConfigSnapshot snapshot = new DatasetParseConfigSnapshot(
            7L, 10L, 201L, 202L, null, null, null,
            new ChunkingConfig(), new EnhancementConfig(), new PdfConfig(), recall, true);

        byte[] bytes = valueSerializer().serialize(DatasetParseConfigCacheEnvelope.found(snapshot));

        JsonNode root = new ObjectMapper().readTree(bytes);
        assertThat(root.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.path("state").asText()).isEqualTo("FOUND");
        assertThat(root.has("found")).isFalse();
        JsonNode value = root.path("value");
        assertThat(value.path("user_id").asLong()).isEqualTo(7L);
        assertThat(value.path("dataset_id").asLong()).isEqualTo(10L);
        assertThat(value.path("sparse_embedding_config_id").asLong()).isEqualTo(201L);
        assertThat(value.path("dense_embedding_config_id").asLong()).isEqualTo(202L);
        assertThat(value.path("recall_config").path("dense_top_k").asInt()).isEqualTo(5);
        assertThat(value.path("recall_config").has("recall_enabled_sources")).isFalse();
        assertThat(value.path("recall_config").has("rerank_top_n")).isFalse();
        assertThat(value.path("recall_config").has("recall_strict")).isFalse();
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("model_bindings");

        Object raw = valueSerializer().deserialize(bytes);
        DatasetParseConfigCacheEnvelope decoded =
            new ObjectMapper().convertValue(raw, DatasetParseConfigCacheEnvelope.class);
        assertThat(decoded.isValidFor(7L, 10L)).isTrue();
        assertThat(decoded.getValue().getRecallConfig().getDenseTopK()).isEqualTo(5);
    }

    @Test
    void envelopeRejectsUnknownVersionAndRouteOrOwnerMismatch() {
        DatasetParseConfigSnapshot snapshot = new DatasetParseConfigSnapshot();
        snapshot.setUserId(7L);
        snapshot.setDatasetId(10L);
        snapshot.setChunkingConfig(new ChunkingConfig());
        snapshot.setEnhancementConfig(new EnhancementConfig());
        snapshot.setPdfConfig(new PdfConfig());
        snapshot.setRecallConfig(new RecallConfig());
        snapshot.setIsActive(true);
        DatasetParseConfigCacheEnvelope envelope = DatasetParseConfigCacheEnvelope.found(snapshot);

        assertThat(envelope.isValidFor(7L, 10L)).isTrue();
        assertThat(envelope.isValidFor(8L, 10L)).isFalse();
        assertThat(envelope.isValidFor(7L, 11L)).isFalse();
        envelope.setSchemaVersion(2);
        assertThat(envelope.isValidFor(7L, 10L)).isFalse();
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<Object> valueSerializer() {
        return (RedisSerializer<Object>) new RedisConfig()
            .redisTemplate(org.mockito.Mockito.mock(RedisConnectionFactory.class))
            .getValueSerializer();
    }
}

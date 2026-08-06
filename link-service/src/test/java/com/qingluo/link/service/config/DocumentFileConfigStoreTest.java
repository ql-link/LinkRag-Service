package com.qingluo.link.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.service.support.DocumentFileConfigMetrics;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DocumentFileConfigStoreTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private DocumentFileConfigMetrics metrics;
    private DocumentFileProperties properties;
    private DocumentFileConfigStore store;

    @BeforeEach
    void setUp() {
        properties = new DocumentFileProperties();
        properties.setMaxSizeBytes(20L);
        properties.setHardMaxSizeBytes(100L);
        properties.setAllowedSuffixes(new LinkedHashSet<>(List.of("md", "pdf")));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new DocumentFileConfigStore(redisTemplate, new ObjectMapper(), properties, metrics);
    }

    @Test
    void missingOverride_usesDefaultsWithoutWritingRedis() {
        when(valueOperations.get(DocumentFileConfigStore.CONFIG_KEY)).thenReturn(null);
        DocumentFileConfigSnapshot result = store.resolve();
        assertThat(result.getMaxSizeBytes()).isEqualTo(20L);
        assertThat(result.getAllowedSuffixes()).containsExactly("md", "pdf");
        verify(valueOperations, never()).set(
            org.mockito.ArgumentMatchers.eq(DocumentFileConfigStore.CONFIG_KEY),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redisFailure_afterValidRead_usesLastValidSnapshot() {
        DocumentFileConfigSnapshot override = new DocumentFileConfigSnapshot(
            10L, new LinkedHashSet<>(List.of("pdf")), 9L, null);
        when(valueOperations.get(DocumentFileConfigStore.CONFIG_KEY))
            .thenReturn(override)
            .thenThrow(new RuntimeException("redis down"));
        assertThat(store.resolve().getMaxSizeBytes()).isEqualTo(10L);
        assertThat(store.resolve().getAllowedSuffixes()).containsExactly("pdf");
        verify(metrics).fallback("redis_unavailable");
    }

    @Test
    void corruptedValue_onColdStart_usesDefaultsWithoutOverwritingEvidence() {
        when(valueOperations.get(DocumentFileConfigStore.CONFIG_KEY)).thenReturn(java.util.Map.of(
            "maxSizeBytes", 1000L,
            "allowedSuffixes", List.of("exe")));
        DocumentFileConfigSnapshot result = store.resolve();
        assertThat(result.getMaxSizeBytes()).isEqualTo(20L);
        verify(metrics).fallback("corrupted");
        verify(valueOperations, never()).set(
            org.mockito.ArgumentMatchers.eq(DocumentFileConfigStore.CONFIG_KEY),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyRedisOverrideContainingTxt_fallsBackToParserContract() {
        properties.setAllowedSuffixes(DocumentFileTypeContract.supportedSuffixes());
        when(valueOperations.get(DocumentFileConfigStore.CONFIG_KEY)).thenReturn(java.util.Map.of(
            "maxSizeBytes", 10L,
            "allowedSuffixes", List.of("md", "pdf", "txt")));

        DocumentFileConfigSnapshot result = store.resolve();

        assertThat(result.getAllowedSuffixes())
            .containsExactly("md", "markdown", "pdf", "docx", "html", "htm");
        verify(metrics).fallback("corrupted");
        verify(valueOperations, never()).set(
            org.mockito.ArgumentMatchers.eq(DocumentFileConfigStore.CONFIG_KEY),
            org.mockito.ArgumentMatchers.any());
    }
}

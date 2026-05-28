package com.qingluo.link.service.config;

import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.service.cache.KnowledgeFileConfigCacheService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeFileConfigInitializerTest {

    @Mock
    private KnowledgeFileConfigCacheService cacheService;

    @Test
    @DisplayName("Should_WriteDefaultConfig_When_ApplicationReady")
    void Should_WriteDefaultConfig_When_ApplicationReady() {
        KnowledgeFileProperties properties = properties(2048L, "pdf", "txt");
        KnowledgeFileConfigInitializer initializer = new KnowledgeFileConfigInitializer(properties, cacheService);

        initializer.initialize();

        ArgumentCaptor<KnowledgeFileConfigDTO> captor = ArgumentCaptor.forClass(KnowledgeFileConfigDTO.class);
        verify(cacheService).putConfigIfAbsent(captor.capture());
        assertThat(captor.getValue().getMaxSizeBytes()).isEqualTo(2048L);
        assertThat(captor.getValue().getAllowedSuffixes()).containsExactly("pdf", "txt");
        assertThat(captor.getValue().getUpdatedBy()).isNull();
        assertThat(captor.getValue().getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Should_Continue_When_RedisInitializationFails")
    void Should_Continue_When_RedisInitializationFails() {
        KnowledgeFileProperties properties = properties(2048L, "pdf");
        KnowledgeFileConfigInitializer initializer = new KnowledgeFileConfigInitializer(properties, cacheService);
        willThrow(new RuntimeException("redis timeout")).given(cacheService)
            .putConfigIfAbsent(org.mockito.ArgumentMatchers.any(KnowledgeFileConfigDTO.class));

        assertThatCode(initializer::initialize).doesNotThrowAnyException();
    }

    private KnowledgeFileProperties properties(long maxSizeBytes, String... suffixes) {
        KnowledgeFileProperties properties = new KnowledgeFileProperties();
        properties.setMaxSizeBytes(maxSizeBytes);
        properties.setAllowedSuffixes(new LinkedHashSet<>(Arrays.asList(suffixes)));
        return properties;
    }
}

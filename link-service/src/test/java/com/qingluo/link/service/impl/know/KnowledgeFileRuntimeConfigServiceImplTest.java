package com.qingluo.link.service.impl.know;

import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.service.cache.KnowledgeFileConfigCacheService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.config.KnowledgeFileRuntimeConfig;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KnowledgeFileRuntimeConfigServiceImplTest {

    @Mock
    private KnowledgeFileConfigCacheService cacheService;

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisConfigMissing")
    void Should_ReturnDefaultConfig_When_RedisConfigMissing() {
        KnowledgeFileProperties properties = properties(1024L, "pdf", "txt");
        KnowledgeFileRuntimeConfigServiceImpl service = new KnowledgeFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.empty());

        KnowledgeFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    @Test
    @DisplayName("Should_ReturnRedisConfig_When_RedisConfigValid")
    void Should_ReturnRedisConfig_When_RedisConfigValid() {
        KnowledgeFileProperties properties = properties(1024L, "pdf", "txt");
        KnowledgeFileRuntimeConfigServiceImpl service = new KnowledgeFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.of(
            new KnowledgeFileConfigDTO(2048L, List.of("PDF"), 10000L, null)));

        KnowledgeFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(2048L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf");
    }

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisReadThrows")
    void Should_ReturnDefaultConfig_When_RedisReadThrows() {
        KnowledgeFileProperties properties = properties(1024L, "pdf", "txt");
        KnowledgeFileRuntimeConfigServiceImpl service = new KnowledgeFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willThrow(new RuntimeException("redis timeout"));

        KnowledgeFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisSuffixUnsupported")
    void Should_ReturnDefaultConfig_When_RedisSuffixUnsupported() {
        KnowledgeFileProperties properties = properties(1024L, "pdf", "txt");
        KnowledgeFileRuntimeConfigServiceImpl service = new KnowledgeFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.of(
            new KnowledgeFileConfigDTO(2048L, List.of("pdf", "exe"), 10000L, null)));

        KnowledgeFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisMaxSizeInvalid")
    void Should_ReturnDefaultConfig_When_RedisMaxSizeInvalid() {
        KnowledgeFileProperties properties = properties(1024L, "pdf", "txt");
        KnowledgeFileRuntimeConfigServiceImpl service = new KnowledgeFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.of(
            new KnowledgeFileConfigDTO(0L, List.of("pdf"), 10000L, null)));

        KnowledgeFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    private KnowledgeFileProperties properties(long maxSizeBytes, String... suffixes) {
        KnowledgeFileProperties properties = new KnowledgeFileProperties();
        properties.setMaxSizeBytes(maxSizeBytes);
        properties.setAllowedSuffixes(new LinkedHashSet<>(Arrays.asList(suffixes)));
        return properties;
    }
}

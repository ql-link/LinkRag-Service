package com.qingluo.link.service.impl.document;

import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.service.cache.DocumentFileConfigCacheService;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
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
class DocumentFileRuntimeConfigServiceImplTest {

    @Mock
    private DocumentFileConfigCacheService cacheService;

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisConfigMissing")
    void Should_ReturnDefaultConfig_When_RedisConfigMissing() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        DocumentFileRuntimeConfigServiceImpl service = new DocumentFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.empty());

        DocumentFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    @Test
    @DisplayName("Should_ReturnRedisConfig_When_RedisConfigValid")
    void Should_ReturnRedisConfig_When_RedisConfigValid() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        DocumentFileRuntimeConfigServiceImpl service = new DocumentFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.of(
            new DocumentFileConfigDTO(2048L, List.of("PDF"), 10000L, null)));

        DocumentFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(2048L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf");
    }

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisReadThrows")
    void Should_ReturnDefaultConfig_When_RedisReadThrows() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        DocumentFileRuntimeConfigServiceImpl service = new DocumentFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willThrow(new RuntimeException("redis timeout"));

        DocumentFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisSuffixUnsupported")
    void Should_ReturnDefaultConfig_When_RedisSuffixUnsupported() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        DocumentFileRuntimeConfigServiceImpl service = new DocumentFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.of(
            new DocumentFileConfigDTO(2048L, List.of("pdf", "exe"), 10000L, null)));

        DocumentFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisMaxSizeInvalid")
    void Should_ReturnDefaultConfig_When_RedisMaxSizeInvalid() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        DocumentFileRuntimeConfigServiceImpl service = new DocumentFileRuntimeConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.of(
            new DocumentFileConfigDTO(0L, List.of("pdf"), 10000L, null)));

        DocumentFileRuntimeConfig result = service.getCurrent();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
    }

    private DocumentFileProperties properties(long maxSizeBytes, String... suffixes) {
        DocumentFileProperties properties = new DocumentFileProperties();
        properties.setMaxSizeBytes(maxSizeBytes);
        properties.setAllowedSuffixes(new LinkedHashSet<>(Arrays.asList(suffixes)));
        return properties;
    }
}

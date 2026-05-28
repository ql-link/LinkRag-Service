package com.qingluo.link.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeFileConfigCacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ObjectMapper objectMapper;
    private KnowledgeFileConfigCacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        cacheService = new KnowledgeFileConfigCacheServiceImpl(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("Should_ReturnConfig_When_RedisContainsJsonString")
    void Should_ReturnConfig_When_RedisContainsJsonString() throws Exception {
        KnowledgeFileConfigDTO config = new KnowledgeFileConfigDTO(
            1024L, List.of("pdf", "txt"), 10000L, LocalDateTime.of(2026, 5, 28, 10, 0));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KnowledgeFileConfigCacheService.CACHE_KEY))
            .willReturn(objectMapper.writeValueAsString(config));

        Optional<KnowledgeFileConfigDTO> result = cacheService.getConfig();

        assertThat(result).isPresent();
        assertThat(result.get().getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.get().getAllowedSuffixes()).containsExactly("pdf", "txt");
        assertThat(result.get().getUpdatedBy()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_RedisReadFails")
    void Should_ReturnEmpty_When_RedisReadFails() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KnowledgeFileConfigCacheService.CACHE_KEY))
            .willThrow(new RuntimeException("redis timeout"));

        Optional<KnowledgeFileConfigDTO> result = cacheService.getConfig();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should_WriteJson_When_PutConfig")
    void Should_WriteJson_When_PutConfig() {
        KnowledgeFileConfigDTO config = new KnowledgeFileConfigDTO(2048L, List.of("md"), 10001L, null);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        cacheService.putConfig(config);

        verify(valueOperations).set(eq(KnowledgeFileConfigCacheService.CACHE_KEY), eq("{\"maxSizeBytes\":2048,\"allowedSuffixes\":[\"md\"],\"updatedBy\":10001,\"updatedAt\":null}"));
    }

    @Test
    @DisplayName("Should_Throw_When_PutConfigFails")
    void Should_Throw_When_PutConfigFails() {
        KnowledgeFileConfigDTO config = new KnowledgeFileConfigDTO(2048L, List.of("md"), 10001L, null);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new RuntimeException("redis timeout")).given(valueOperations)
            .set(eq(KnowledgeFileConfigCacheService.CACHE_KEY), eq("{\"maxSizeBytes\":2048,\"allowedSuffixes\":[\"md\"],\"updatedBy\":10001,\"updatedAt\":null}"));

        assertThatThrownBy(() -> cacheService.putConfig(config))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("redis timeout");
    }

    @Test
    @DisplayName("Should_WriteOnlyWhenAbsent_When_PutConfigIfAbsent")
    void Should_WriteOnlyWhenAbsent_When_PutConfigIfAbsent() {
        KnowledgeFileConfigDTO config = new KnowledgeFileConfigDTO(4096L, List.of("pdf"), null, null);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(
            eq(KnowledgeFileConfigCacheService.CACHE_KEY),
            eq("{\"maxSizeBytes\":4096,\"allowedSuffixes\":[\"pdf\"],\"updatedBy\":null,\"updatedAt\":null}")))
            .willReturn(Boolean.TRUE);

        boolean result = cacheService.putConfigIfAbsent(config);

        assertThat(result).isTrue();
    }
}

package com.qingluo.link.service.impl.admin;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.request.UpdateDocumentFileConfigRequest;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.service.cache.DocumentFileConfigCacheService;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDocumentFileConfigServiceImplTest {

    @Mock
    private DocumentFileConfigCacheService cacheService;

    @Test
    @DisplayName("Should_ReturnRedisConfig_When_ConfigExists")
    void Should_ReturnRedisConfig_When_ConfigExists() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        AdminDocumentFileConfigServiceImpl service = new AdminDocumentFileConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.of(
            new DocumentFileConfigDTO(2048L, List.of("PDF", "txt"), 10000L, null)));

        DocumentFileConfigDTO result = service.getCurrentConfig();

        assertThat(result.getMaxSizeBytes()).isEqualTo(2048L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
        assertThat(result.getUpdatedBy()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should_ReturnDefaultConfig_When_RedisConfigMissing")
    void Should_ReturnDefaultConfig_When_RedisConfigMissing() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        AdminDocumentFileConfigServiceImpl service = new AdminDocumentFileConfigServiceImpl(properties, cacheService);
        given(cacheService.getConfig()).willReturn(Optional.empty());

        DocumentFileConfigDTO result = service.getCurrentConfig();

        assertThat(result.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "txt");
        assertThat(result.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("Should_WriteRedisConfig_When_UpdateConfigValid")
    void Should_WriteRedisConfig_When_UpdateConfigValid() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        AdminDocumentFileConfigServiceImpl service = new AdminDocumentFileConfigServiceImpl(properties, cacheService);
        UpdateDocumentFileConfigRequest request = new UpdateDocumentFileConfigRequest();
        request.setMaxSizeBytes(4096L);
        request.setAllowedSuffixes(List.of("PDF", "txt", "pdf"));

        service.updateConfig(10000L, request);

        ArgumentCaptor<DocumentFileConfigDTO> captor = ArgumentCaptor.forClass(DocumentFileConfigDTO.class);
        verify(cacheService).putConfig(captor.capture());
        assertThat(captor.getValue().getMaxSizeBytes()).isEqualTo(4096L);
        assertThat(captor.getValue().getAllowedSuffixes()).containsExactly("pdf", "txt");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(10000L);
        assertThat(captor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should_ThrowBusinessException_When_RedisWriteFails")
    void Should_ThrowBusinessException_When_RedisWriteFails() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        AdminDocumentFileConfigServiceImpl service = new AdminDocumentFileConfigServiceImpl(properties, cacheService);
        UpdateDocumentFileConfigRequest request = new UpdateDocumentFileConfigRequest();
        request.setMaxSizeBytes(4096L);
        request.setAllowedSuffixes(List.of("pdf"));
        willThrow(new RuntimeException("redis timeout")).given(cacheService)
            .putConfig(org.mockito.ArgumentMatchers.any(DocumentFileConfigDTO.class));

        assertThatThrownBy(() -> service.updateConfig(10000L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessage("配置保存失败，请稍后重试");
    }

    @Test
    @DisplayName("Should_RejectUnsupportedSuffix_When_UpdateConfig")
    void Should_RejectUnsupportedSuffix_When_UpdateConfig() {
        DocumentFileProperties properties = properties(1024L, "pdf", "txt");
        AdminDocumentFileConfigServiceImpl service = new AdminDocumentFileConfigServiceImpl(properties, cacheService);
        UpdateDocumentFileConfigRequest request = new UpdateDocumentFileConfigRequest();
        request.setMaxSizeBytes(4096L);
        request.setAllowedSuffixes(List.of("exe"));

        assertThatThrownBy(() -> service.updateConfig(10000L, request))
            .isInstanceOf(BusinessException.class);
    }

    private DocumentFileProperties properties(long maxSizeBytes, String... suffixes) {
        DocumentFileProperties properties = new DocumentFileProperties();
        properties.setMaxSizeBytes(maxSizeBytes);
        properties.setAllowedSuffixes(new LinkedHashSet<>(Arrays.asList(suffixes)));
        return properties;
    }
}

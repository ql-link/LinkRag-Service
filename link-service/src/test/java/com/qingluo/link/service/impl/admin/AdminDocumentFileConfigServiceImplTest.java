package com.qingluo.link.service.impl.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.request.UpdateDocumentFileConfigRequest;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.DocumentFileConfigSnapshot;
import com.qingluo.link.service.config.DocumentFileConfigStore;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.support.DocumentFileConfigReadiness;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDocumentFileConfigServiceImplTest {

    @Mock private DocumentFileConfigStore store;
    @Mock private DocumentFileConfigReadiness readiness;
    private DocumentFileProperties properties;
    private AdminDocumentFileConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DocumentFileProperties();
        properties.setAllowedSuffixes(new LinkedHashSet<>(List.of("pdf", "md")));
        service = new AdminDocumentFileConfigServiceImpl(store, properties, readiness);
    }

    @Test
    void getCurrentConfig_returnsResolvedValue() {
        when(store.resolve()).thenReturn(new DocumentFileConfigSnapshot(
            10_485_760L, new LinkedHashSet<>(List.of("pdf")), 7L, null));
        DocumentFileConfigDTO result = service.getCurrentConfig();
        assertThat(result.getMaxSizeBytes()).isEqualTo(10_485_760L);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf");
        assertThat(result.getUpdatedBy()).isEqualTo(7L);
    }

    @Test
    void updateConfig_writesCompleteSnapshotOnlyAfterReadiness() {
        when(readiness.isReady()).thenReturn(true);
        UpdateDocumentFileConfigRequest request = new UpdateDocumentFileConfigRequest();
        request.setMaxSizeBytes(5_242_880L);
        request.setAllowedSuffixes(List.of("PDF", "pdf"));
        DocumentFileConfigDTO result = service.updateConfig(9L, request);
        assertThat(result.getAllowedSuffixes()).containsExactly("pdf");
        verify(store).write(org.mockito.ArgumentMatchers.argThat(snapshot ->
            snapshot.getMaxSizeBytes() == 5_242_880L
                && snapshot.getUpdatedBy().equals(9L)
                && snapshot.getAllowedSuffixes().equals(new LinkedHashSet<>(List.of("pdf")))));
    }

    @Test
    void updateConfig_normalizesDeploymentSuffixCase() {
        properties.setAllowedSuffixes(new LinkedHashSet<>(List.of("PDF", "Md")));
        when(readiness.isReady()).thenReturn(true);
        UpdateDocumentFileConfigRequest request = new UpdateDocumentFileConfigRequest();
        request.setMaxSizeBytes(1024L);
        request.setAllowedSuffixes(List.of("pdf", "MD"));

        DocumentFileConfigDTO result = service.updateConfig(9L, request);

        assertThat(result.getAllowedSuffixes()).containsExactly("pdf", "md");
    }

    @Test
    void updateConfig_rejectsUnsupportedSuffixWithoutWrite() {
        when(readiness.isReady()).thenReturn(true);
        UpdateDocumentFileConfigRequest request = new UpdateDocumentFileConfigRequest();
        request.setMaxSizeBytes(1024L);
        request.setAllowedSuffixes(List.of("exe"));
        assertThatThrownBy(() -> service.updateConfig(9L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.DOCUMENT_FILE_CONFIG_INVALID.getCode());
    }

    @Test
    void updateConfig_redisWriteFailureKeepsOldLocalStateAndReturns503() {
        when(readiness.isReady()).thenReturn(true);
        doThrow(new RuntimeException("redis down")).when(store).write(org.mockito.ArgumentMatchers.any());
        UpdateDocumentFileConfigRequest request = new UpdateDocumentFileConfigRequest();
        request.setMaxSizeBytes(1024L);
        request.setAllowedSuffixes(List.of("pdf"));
        assertThatThrownBy(() -> service.updateConfig(9L, request))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(ErrorCode.DOCUMENT_FILE_CONFIG_UPDATE_FAILED.getCode());
    }
}

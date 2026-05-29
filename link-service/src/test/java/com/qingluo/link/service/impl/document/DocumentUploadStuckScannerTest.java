package com.qingluo.link.service.impl.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.service.config.DocumentUploadAsyncProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * uploading 超时扫描测试，覆盖 S10（超阈值置 failed）、S11（可读 failureReason）。
 */
@ExtendWith(MockitoExtension.class)
class DocumentUploadStuckScannerTest {

    @Mock
    private DocumentOriginalFileMapper documentOriginalFileMapper;
    @Mock
    private DocumentUploadStatusWriter statusWriter;
    @Mock
    private DocumentUploadAsyncProperties properties;

    @InjectMocks
    private DocumentUploadStuckScanner scanner;

    @Test
    @DisplayName("S10/S11 超阈值仍 uploading 的记录被置 failed（上传超时，请重试）")
    void scan_marksStuckFailed() {
        given(properties.getStuckThreshold()).willReturn(Duration.ofMinutes(10));
        DocumentOriginalFile stuck = new DocumentOriginalFile();
        stuck.setId(7L);
        stuck.setDatasetId(200L);
        stuck.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        given(documentOriginalFileMapper.selectList(any())).willReturn(List.of(stuck));

        scanner.scan();

        verify(statusWriter).markUploadFailed(7L, "上传超时，请重试");
    }

    @Test
    @DisplayName("S10 无超时记录时不置 failed")
    void scan_noStuck_noAction() {
        given(properties.getStuckThreshold()).willReturn(Duration.ofMinutes(10));
        given(documentOriginalFileMapper.selectList(any())).willReturn(List.of());

        scanner.scan();

        verify(statusWriter, never()).markUploadFailed(any(), any());
    }
}

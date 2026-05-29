package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.service.DocumentFileRuntimeConfigService;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.mq.DocumentParseTaskMQ;

import com.qingluo.link.service.impl.document.DocumentFileServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class DocumentFileServiceImplTest {

    @Mock
    private DatasetMapper datasetMapper;

    @Mock
    private DocumentOriginalFileMapper documentOriginalFileMapper;

    @Mock
    private DocumentParseFileMapper documentParseFileMapper;

    @Mock
    private DocumentParsedLogMapper documentParsedLogMapper;

    @Mock
    private IOssService ossService;

    @Mock
    private PrivateFileResolver privateFileResolver;

    @Mock
    private ObjectProvider<MQSend> mqSendProvider;

    @Mock
    private DocumentFileProperties properties;

    @Mock
    private DocumentFileRuntimeConfigService documentFileRuntimeConfigService;

    @InjectMocks
    private DocumentFileServiceImpl documentFileService;

    @Test
    @DisplayName("Should_CreateParseTaskMq_When_NoArgConstructor")
    void Should_CreateParseTaskMq_When_NoArgConstructor() {
        AbstractMQ mq = new DocumentParseTaskMQ();

        assertEquals("tolink.rag.parse_task", mq.getMQName());
    }

    @Test
    @DisplayName("Should_NotDeleteDatabaseRecord_When_OssDeleteFails")
    void Should_NotDeleteDatabaseRecord_When_OssDeleteFails() {
        DocumentOriginalFile file = buildFile(1L, 100L, "raw/100/200/test.txt");
        given(documentOriginalFileMapper.selectOne(any())).willReturn(file);
        given(ossService.deleteFile(any(), any())).willReturn(false);

        assertThatThrownBy(() -> documentFileService.delete(100L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("删除原文件失败，请稍后重试");

        verify(documentOriginalFileMapper).selectOne(any());
        verifyNoMoreInteractions(documentOriginalFileMapper);
    }

    @Test
    @DisplayName("Should_ThrowCompensationException_When_DatabaseDeleteFailsAfterOssDelete")
    void Should_ThrowCompensationException_When_DatabaseDeleteFailsAfterOssDelete() {
        DocumentOriginalFile file = buildFile(1L, 100L, "raw/100/200/test.txt");
        given(documentOriginalFileMapper.selectOne(any())).willReturn(file);
        given(ossService.deleteFile(any(), any())).willReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("db delete failed"))
            .when(documentOriginalFileMapper).deleteById((java.io.Serializable) 1L);

        assertThatThrownBy(() -> documentFileService.delete(100L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("原文件对象已删除，但数据库记录删除失败，请尽快补偿处理");

        verify(privateFileResolver).evictPrivateFile("raw/100/200/test.txt");
    }

    private DocumentOriginalFile buildFile(Long fileId, Long userId, String objectKey) {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setId(fileId);
        file.setUserId(userId);
        file.setObjectKey(objectKey);
        return file;
    }
}

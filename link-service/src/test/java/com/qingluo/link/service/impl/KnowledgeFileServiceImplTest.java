package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.service.KnowledgeFileRuntimeConfigService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class KnowledgeFileServiceImplTest {

    @Mock
    private DatasetMapper datasetMapper;

    @Mock
    private KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;

    @Mock
    private KnowledgeParsedFileMapper knowledgeParsedFileMapper;

    @Mock
    private IOssService ossService;

    @Mock
    private PrivateFileResolver privateFileResolver;

    @Mock
    private ObjectProvider<MQSend> mqSendProvider;

    @Mock
    private KnowledgeFileProperties properties;

    @Mock
    private KnowledgeFileRuntimeConfigService knowledgeFileRuntimeConfigService;

    @InjectMocks
    private KnowledgeFileServiceImpl knowledgeFileService;

    @Test
    @DisplayName("Should_CreateParseTaskMq_When_NoArgConstructor")
    void Should_CreateParseTaskMq_When_NoArgConstructor() throws Exception {
        Class<?> mqClass = Class.forName("com.qingluo.link.service.impl.KnowledgeFileServiceImpl$KnowledgeParseTaskMQ");
        Constructor<?> constructor = mqClass.getDeclaredConstructor();
        constructor.setAccessible(true);

        AbstractMQ mq = (AbstractMQ) BeanUtils.instantiateClass(constructor);

        assertEquals("tolink.rag.parse_task", mq.getMQName());
    }

    @Test
    @DisplayName("Should_NotDeleteDatabaseRecord_When_OssDeleteFails")
    void Should_NotDeleteDatabaseRecord_When_OssDeleteFails() {
        KnowledgeOriginalFile file = buildFile(1L, 100L, "raw/100/200/test.txt");
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
        given(ossService.deleteFile(any(), any())).willReturn(false);

        assertThatThrownBy(() -> knowledgeFileService.delete(100L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("删除原文件失败，请稍后重试");

        verify(knowledgeOriginalFileMapper).selectOne(any());
        verifyNoMoreInteractions(knowledgeOriginalFileMapper);
    }

    @Test
    @DisplayName("Should_ThrowCompensationException_When_DatabaseDeleteFailsAfterOssDelete")
    void Should_ThrowCompensationException_When_DatabaseDeleteFailsAfterOssDelete() {
        KnowledgeOriginalFile file = buildFile(1L, 100L, "raw/100/200/test.txt");
        given(knowledgeOriginalFileMapper.selectOne(any())).willReturn(file);
        given(ossService.deleteFile(any(), any())).willReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("db delete failed"))
            .when(knowledgeOriginalFileMapper).deleteById((java.io.Serializable) 1L);

        assertThatThrownBy(() -> knowledgeFileService.delete(100L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("原文件对象已删除，但数据库记录删除失败，请尽快补偿处理");

        verify(privateFileResolver).evictPrivateFile("raw/100/200/test.txt");
    }

    private KnowledgeOriginalFile buildFile(Long fileId, Long userId, String objectKey) {
        KnowledgeOriginalFile file = new KnowledgeOriginalFile();
        file.setId(fileId);
        file.setUserId(userId);
        file.setObjectKey(objectKey);
        return file;
    }
}

package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetServiceImplTest {

    @Mock
    private DatasetMapper datasetMapper;

    @Mock
    private ChatConversationMapper chatConversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private DocumentOriginalFileMapper documentOriginalFileMapper;

    @Mock
    private IOssService ossService;

    @Mock
    private PrivateFileResolver privateFileResolver;

    @InjectMocks
    private DatasetServiceImpl datasetService;

    @Test
    @DisplayName("Should_StopDatabaseDeletion_When_AnyOssFileDeleteFails")
    void Should_StopDatabaseDeletion_When_AnyOssFileDeleteFails() {
        Dataset dataset = buildDataset(10L, 100L);
        DocumentOriginalFile file = buildFile("raw/100/10/a.txt");
        given(datasetMapper.selectOne(any())).willReturn(dataset);
        given(documentOriginalFileMapper.selectList(any())).willReturn(List.of(file));
        given(ossService.deleteFile(any(), any())).willReturn(false);

        assertThatThrownBy(() -> datasetService.delete(100L, 10L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("删除数据集原文件失败，请稍后重试");

        verify(datasetMapper).selectOne(any());
        verify(chatConversationMapper, never()).delete(any());
        verify(documentOriginalFileMapper, never()).delete(any());
        verifyNoMoreInteractions(datasetMapper);
    }

    @Test
    @DisplayName("Should_ThrowCompensationException_When_DatabaseDeleteFailsAfterAllOssFilesDeleted")
    void Should_ThrowCompensationException_When_DatabaseDeleteFailsAfterAllOssFilesDeleted() {
        Dataset dataset = buildDataset(10L, 100L);
        DocumentOriginalFile file = buildFile("raw/100/10/a.txt");
        ChatConversation conversation = new ChatConversation();
        conversation.setId(99L);
        conversation.setDatasetId(10L);
        given(datasetMapper.selectOne(any())).willReturn(dataset);
        given(documentOriginalFileMapper.selectList(any())).willReturn(List.of(file));
        given(chatConversationMapper.selectList(any())).willReturn(List.of(conversation));
        given(ossService.deleteFile(any(), any())).willReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("db delete failed"))
            .when(datasetMapper).deleteById((java.io.Serializable) 10L);

        assertThatThrownBy(() -> datasetService.delete(100L, 10L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("数据集原始对象已删除，但数据库记录删除失败，请尽快补偿处理");

        verify(privateFileResolver).evictPrivateFile("raw/100/10/a.txt");
    }

    private Dataset buildDataset(Long datasetId, Long userId) {
        Dataset dataset = new Dataset();
        dataset.setId(datasetId);
        dataset.setUserId(userId);
        return dataset;
    }

    private DocumentOriginalFile buildFile(String objectKey) {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setObjectKey(objectKey);
        return file;
    }
}

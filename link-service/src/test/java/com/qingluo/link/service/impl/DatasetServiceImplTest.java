package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.service.delete.DocumentDeleteNotifier;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 数据集隐性删除单元测试：软删原文件/数据集（不删 OSS、不物理删行），物理删会话/消息，
 * 提交后通知 Python 删衍生产物（dataset 范围）。无真实事务时 notifyDatasetDeletedAfterCommit 走直接调用分支。
 */
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
    private DocumentDeleteNotifier deleteNotifier;

    @InjectMocks
    private DatasetServiceImpl datasetService;

    /** 单元测试无 MyBatis 启动扫描，手动初始化 MP TableInfo，使软删的 LambdaUpdateWrapper 能解析列。 */
    @BeforeAll
    static void initMpTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Dataset.class);
        TableInfoHelper.initTableInfo(assistant, DocumentOriginalFile.class);
    }

    @Test
    @DisplayName("Should_SoftDeleteFilesAndDatasetWithoutOss_When_DeleteDataset")
    void Should_SoftDeleteFilesAndDatasetWithoutOss_When_DeleteDataset() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(99L);
        conversation.setDatasetId(10L);
        given(datasetMapper.selectOne(any())).willReturn(buildDataset(10L, 100L));
        given(chatConversationMapper.selectList(any())).willReturn(List.of(conversation));

        datasetService.delete(100L, 10L);

        // 原文件软删（update），不物理删 DB 行、不删 OSS（已无 OSS 依赖注入）
        verify(documentOriginalFileMapper).update(any(), any());
        verify(documentOriginalFileMapper, never()).delete(any());
        verify(documentOriginalFileMapper, never()).deleteById(any(Long.class));
        // 数据集软删（update），不物理删
        verify(datasetMapper).update(any(), any());
        verify(datasetMapper, never()).deleteById(any(Long.class));
        // 会话与消息一律物理删
        verify(chatMessageMapper).delete(any());
        verify(chatConversationMapper).delete(any());
    }

    @Test
    @DisplayName("Should_NotifyDatasetScope_When_DeleteDatasetCommitted")
    void Should_NotifyDatasetScope_When_DeleteDatasetCommitted() {
        given(datasetMapper.selectOne(any())).willReturn(buildDataset(10L, 100L));
        given(chatConversationMapper.selectList(any())).willReturn(List.of());

        datasetService.delete(100L, 10L);

        // dataset 范围通知：仅按 datasetId + userId 投递一条，不下发文件 id（与名下文件数量无关）。
        // 生产侧不再枚举文件，故该断言同时覆盖「多文件仍一条」「空数据集仍发」。
        verify(deleteNotifier, times(1)).notifyDatasetDeleted(10L, 100L);
        verify(deleteNotifier, never()).notifyFileDeleted(any(), any(), any());
    }

    @Test
    @DisplayName("Should_NotNotifyPython_When_SoftDeleteFails")
    void Should_NotNotifyPython_When_SoftDeleteFails() {
        given(datasetMapper.selectOne(any())).willReturn(buildDataset(10L, 100L));
        given(documentOriginalFileMapper.update(any(), any())).willThrow(new RuntimeException("db fail"));

        assertThatThrownBy(() -> datasetService.delete(100L, 10L))
            .isInstanceOf(RuntimeException.class);

        // 删除中途异常（事务将回滚），不触发 Python 删除通知
        verify(deleteNotifier, never()).notifyDatasetDeleted(any(), any());
    }

    @Test
    @DisplayName("Should_Return404AndNotDelete_When_DatasetNotOwned")
    void Should_Return404AndNotDelete_When_DatasetNotOwned() {
        given(datasetMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> datasetService.delete(100L, 10L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("数据集不存在或无权访问");

        verify(documentOriginalFileMapper, never()).update(any(), any());
        verify(datasetMapper, never()).update(any(), any());
        verify(deleteNotifier, never()).notifyDatasetDeleted(any(), any());
    }

    private Dataset buildDataset(Long datasetId, Long userId) {
        Dataset dataset = new Dataset();
        dataset.setId(datasetId);
        dataset.setUserId(userId);
        return dataset;
    }

}

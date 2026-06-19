package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.mapper.UsageLogMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.ChatMessage;
import com.qingluo.link.model.dto.entity.UsageLog;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 对话轮次落库单测：success/partial/failed 三类落库、request_id 幂等去重、conversation 归属校验。
 */
@ExtendWith(MockitoExtension.class)
class ChatTurnPersistenceServiceImplTest {

    @Mock
    private ChatConversationMapper conversationMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private UsageLogMapper usageLogMapper;

    @InjectMocks
    private ChatTurnPersistenceServiceImpl service;

    /** 单元测试无 MyBatis 启动扫描，手动初始化 MP TableInfo，使 LambdaQueryWrapper 能解析列。 */
    @BeforeAll
    static void initMpTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChatMessage.class);
        TableInfoHelper.initTableInfo(assistant, ChatConversation.class);
        TableInfoHelper.initTableInfo(assistant, UsageLog.class);
    }

    private ChatTurnMQ.MsgPayload payload(String status) {
        ChatTurnMQ.MsgPayload p = new ChatTurnMQ.MsgPayload();
        p.setConversationId(100L);
        p.setRequestId("req-1");
        p.setUserId(42L);
        p.setQuery("什么是RAG");
        p.setAnswer("RAG 是检索增强生成");
        p.setConfigId(7L);
        p.setProviderType("openai");
        p.setModelName("gpt-4");
        p.setPromptTokens(120);
        p.setCompletionTokens(80);
        p.setTotalTokens(200);
        p.setReferences(List.of("chunk-1", "chunk-2"));
        p.setLatencyMs(350);
        p.setStatus(status);
        return p;
    }

    private ChatConversation conversation(Long id, Long userId) {
        ChatConversation c = new ChatConversation();
        c.setId(id);
        c.setUserId(userId);
        c.setDatasetId(10L);
        c.setTitle("新对话");
        return c;
    }

    @Test
    @DisplayName("Should_PersistMessageUsageAndConversation_When_Success")
    void Should_PersistAll_When_Success() {
        given(messageMapper.selectCount(any())).willReturn(0L);
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        // 标题冲突预检：无冲突
        given(conversationMapper.selectCount(any())).willReturn(0L);

        service.persist(payload("success"));

        // chat_message 落库
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        ChatMessage saved = msgCaptor.getValue();
        assertThat(saved.getQuery()).isEqualTo("什么是RAG");
        assertThat(saved.getAnswer()).isEqualTo("RAG 是检索增强生成");
        assertThat(saved.getReferences()).containsExactly("chunk-1", "chunk-2");
        assertThat(saved.getRequestId()).isEqualTo("req-1");
        assertThat(saved.getStatus()).isEqualTo("success");

        // llm_usage_log 落库并关联 conversation/request
        ArgumentCaptor<UsageLog> usageCaptor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogMapper).insert(usageCaptor.capture());
        UsageLog usage = usageCaptor.getValue();
        assertThat(usage.getConversationId()).isEqualTo(100L);
        assertThat(usage.getRequestId()).isEqualTo("req-1");
        assertThat(usage.getTotalTokens()).isEqualTo(200);
        assertThat(usage.getUserId()).isEqualTo(42L);

        // chat_conversation 更新：last_config/last_model + 首轮标题
        ArgumentCaptor<ChatConversation> convCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationMapper).updateById(convCaptor.capture());
        ChatConversation conv = convCaptor.getValue();
        assertThat(conv.getLastConfigId()).isEqualTo(7L);
        assertThat(conv.getLastModelName()).isEqualTo("gpt-4");
        assertThat(conv.getTitle()).isEqualTo("什么是RAG");
    }

    @Test
    @DisplayName("Should_PersistEmptyAnswer_When_Failed")
    void Should_Persist_When_Failed() {
        given(messageMapper.selectCount(any())).willReturn(0L);
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(conversationMapper.selectCount(any())).willReturn(0L);

        ChatTurnMQ.MsgPayload p = payload("failed");
        p.setAnswer("");
        p.setPromptTokens(0);
        p.setCompletionTokens(0);
        p.setTotalTokens(0);

        service.persist(p);

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getStatus()).isEqualTo("failed");
        assertThat(msgCaptor.getValue().getAnswer()).isEmpty();
        verify(usageLogMapper).insert(any());
    }

    @Test
    @DisplayName("Should_SkipAll_When_RequestIdAlreadyPersisted")
    void Should_Skip_When_Duplicate() {
        given(messageMapper.selectCount(any())).willReturn(1L);

        service.persist(payload("success"));

        verify(conversationMapper, never()).selectOne(any());
        verify(messageMapper, never()).insert(any());
        verify(usageLogMapper, never()).insert(any());
        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_DropAndNotPersist_When_ConversationNotOwnedByUser")
    void Should_Drop_When_OwnershipMismatch() {
        given(messageMapper.selectCount(any())).willReturn(0L);
        // conversation 属于其他用户
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 999L));

        service.persist(payload("success"));

        verify(messageMapper, never()).insert(any());
        verify(usageLogMapper, never()).insert(any());
        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_DropAndNotPersist_When_ConversationMissing")
    void Should_Drop_When_ConversationMissing() {
        given(messageMapper.selectCount(any())).willReturn(0L);
        given(conversationMapper.selectOne(any())).willReturn(null);

        service.persist(payload("partial"));

        verify(messageMapper, never()).insert(any());
        verify(usageLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should_KeepDefaultTitle_When_TitleCollision")
    void Should_KeepTitle_When_Collision() {
        given(messageMapper.selectCount(any())).willReturn(0L);
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        // 标题预检命中冲突
        given(conversationMapper.selectCount(any())).willReturn(1L);

        service.persist(payload("success"));

        ArgumentCaptor<ChatConversation> convCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationMapper).updateById(convCaptor.capture());
        assertThat(convCaptor.getValue().getTitle()).isEqualTo("新对话");
    }
}

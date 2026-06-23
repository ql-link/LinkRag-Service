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
import com.qingluo.link.service.ConversationTitleService;
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
 * 对话轮次落库单测：按 turn_id upsert（起点插行 / 终态更新同行）、状态不回退、幂等、归属校验、用量只在终态入账。
 */
@ExtendWith(MockitoExtension.class)
class ChatTurnPersistenceServiceImplTest {

    @Mock
    private ChatConversationMapper conversationMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private UsageLogMapper usageLogMapper;

    @Mock
    private ConversationTitleService conversationTitleService;

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
        p.setTurnId("turn-1");
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

    private ChatMessage existingMessage(String status) {
        ChatMessage m = new ChatMessage();
        m.setId(555L);
        m.setConversationId(100L);
        m.setTurnId("turn-1");
        m.setStatus(status);
        return m;
    }

    @Test
    @DisplayName("Should_InsertRowUsageAndConversation_When_FreshCompleted")
    void Should_PersistAll_When_FreshCompleted() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(null);
        given(conversationTitleService.buildFallbackTitle("什么是RAG")).willReturn("什么是RAG");

        service.persist(payload("COMPLETED"));

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        ChatMessage saved = msgCaptor.getValue();
        assertThat(saved.getTurnId()).isEqualTo("turn-1");
        assertThat(saved.getQuery()).isEqualTo("什么是RAG");
        assertThat(saved.getAnswer()).isEqualTo("RAG 是检索增强生成");
        assertThat(saved.getReferences()).containsExactly("chunk-1", "chunk-2");
        assertThat(saved.getRequestId()).isEqualTo("req-1");
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<UsageLog> usageCaptor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogMapper).insert(usageCaptor.capture());
        UsageLog usage = usageCaptor.getValue();
        assertThat(usage.getConversationId()).isEqualTo(100L);
        assertThat(usage.getRequestId()).isEqualTo("req-1");
        assertThat(usage.getTotalTokens()).isEqualTo(200);
        assertThat(usage.getUserId()).isEqualTo(42L);
        assertThat(usage.getStatus()).isEqualTo("success");

        ArgumentCaptor<ChatConversation> convCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationMapper).updateById(convCaptor.capture());
        ChatConversation conv = convCaptor.getValue();
        assertThat(conv.getLastConfigId()).isEqualTo(7L);
        assertThat(conv.getLastModelName()).isEqualTo("gpt-4");
        assertThat(conv.getTitle()).isEqualTo("什么是RAG");
        verify(conversationTitleService).generateAfterCommit(100L, 42L, 7L, "什么是RAG", "RAG 是检索增强生成", "什么是RAG");
    }

    @Test
    @DisplayName("Should_InsertGeneratingRowWithoutUsageOrTitleGen_When_FreshGenerating")
    void Should_InsertGeneratingRow_When_FreshGenerating() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(null);
        given(conversationTitleService.buildFallbackTitle("什么是RAG")).willReturn("什么是RAG");

        ChatTurnMQ.MsgPayload p = payload("GENERATING");
        p.setAnswer("");

        service.persist(p);

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getStatus()).isEqualTo("GENERATING");
        // GENERATING 无 token，不入账，不调模型生成标题（但可设临时标题）。
        verify(usageLogMapper, never()).insert(any());
        verify(conversationTitleService, never()).generateAfterCommit(any(), any(), any(), any(), any(), any());
        ArgumentCaptor<ChatConversation> convCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationMapper).updateById(convCaptor.capture());
        assertThat(convCaptor.getValue().getTitle()).isEqualTo("什么是RAG");
    }

    @Test
    @DisplayName("Should_PersistErrorFields_When_Failed")
    void Should_Persist_When_Failed() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(null);

        ChatTurnMQ.MsgPayload p = payload("FAILED");
        p.setAnswer("");
        p.setPromptTokens(0);
        p.setCompletionTokens(0);
        p.setTotalTokens(0);
        p.setErrorCode("GENERATION_TIMEOUT");
        p.setErrorMessage("timed out");

        service.persist(p);

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(msgCaptor.getValue().getAnswer()).isEmpty();
        assertThat(msgCaptor.getValue().getErrorCode()).isEqualTo("GENERATION_TIMEOUT");
        assertThat(msgCaptor.getValue().getErrorMessage()).isEqualTo("timed out");

        ArgumentCaptor<UsageLog> usageCaptor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogMapper).insert(usageCaptor.capture());
        assertThat(usageCaptor.getValue().getStatus()).isEqualTo("failed");
        // 失败终态不调模型生成自然标题。
        verify(conversationTitleService, never()).generateAfterCommit(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should_UpdateSameRowAndBookUsageOnce_When_GeneratingPromotedToCompleted")
    void Should_PromoteSameRow_When_GeneratingToCompleted() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("GENERATING"));
        given(conversationTitleService.buildFallbackTitle("什么是RAG")).willReturn("什么是RAG");

        service.persist(payload("COMPLETED"));

        // 更新同一行（不再插入新行）。
        verify(messageMapper, never()).insert(any());
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).updateById(msgCaptor.capture());
        ChatMessage updated = msgCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(555L);
        assertThat(updated.getStatus()).isEqualTo("COMPLETED");
        assertThat(updated.getAnswer()).isEqualTo("RAG 是检索增强生成");
        // 终态补一次用量账本，关联既有 message_id。
        ArgumentCaptor<UsageLog> usageCaptor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogMapper).insert(usageCaptor.capture());
        assertThat(usageCaptor.getValue().getMessageId()).isEqualTo(555L);
    }

    @Test
    @DisplayName("Should_NotRegress_When_LateGeneratingArrivesAfterTerminal")
    void Should_NotRegress_When_LateGeneratingAfterTerminal() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("COMPLETED"));

        service.persist(payload("GENERATING"));

        verify(messageMapper, never()).insert(any());
        verify(messageMapper, never()).updateById(any());
        verify(usageLogMapper, never()).insert(any());
        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_SkipDuplicateTerminal_When_TerminalRedelivered")
    void Should_Skip_When_TerminalRedelivered() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("COMPLETED"));

        service.persist(payload("COMPLETED"));

        verify(messageMapper, never()).insert(any());
        verify(messageMapper, never()).updateById(any());
        verify(usageLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should_SkipDuplicateGenerating_When_GeneratingRedelivered")
    void Should_Skip_When_GeneratingRedelivered() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("GENERATING"));

        service.persist(payload("GENERATING"));

        verify(messageMapper, never()).insert(any());
        verify(messageMapper, never()).updateById(any());
        verify(usageLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should_DropAndNotPersist_When_ConversationNotOwnedByUser")
    void Should_Drop_When_OwnershipMismatch() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 999L));

        service.persist(payload("COMPLETED"));

        verify(messageMapper, never()).selectOne(any());
        verify(messageMapper, never()).insert(any());
        verify(usageLogMapper, never()).insert(any());
        verify(conversationMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_DropAndNotPersist_When_ConversationMissing")
    void Should_Drop_When_ConversationMissing() {
        given(conversationMapper.selectOne(any())).willReturn(null);

        service.persist(payload("COMPLETED"));

        verify(messageMapper, never()).insert(any());
        verify(usageLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should_NotScheduleTitleGeneration_When_TitleAlreadyManual")
    void Should_NotScheduleTitleGeneration_When_TitleAlreadyManual() {
        ChatConversation manual = conversation(100L, 42L);
        manual.setTitle("用户手动标题");
        given(conversationMapper.selectOne(any())).willReturn(manual);
        given(messageMapper.selectOne(any())).willReturn(null);
        given(conversationTitleService.buildFallbackTitle("什么是RAG")).willReturn("什么是RAG");

        service.persist(payload("COMPLETED"));

        ArgumentCaptor<ChatConversation> convCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversationMapper).updateById(convCaptor.capture());
        assertThat(convCaptor.getValue().getTitle()).isEqualTo("用户手动标题");
        verify(conversationTitleService, never()).generateAfterCommit(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should_ScheduleTitleGeneration_When_CurrentTitleEqualsFallback")
    void Should_ScheduleTitleGeneration_When_CurrentTitleEqualsFallback() {
        ChatConversation autoTitle = conversation(100L, 42L);
        autoTitle.setTitle("什么是RAG");
        given(conversationMapper.selectOne(any())).willReturn(autoTitle);
        given(messageMapper.selectOne(any())).willReturn(null);
        given(conversationTitleService.buildFallbackTitle("什么是RAG")).willReturn("什么是RAG");

        service.persist(payload("COMPLETED"));

        verify(conversationTitleService).generateAfterCommit(100L, 42L, 7L, "什么是RAG", "RAG 是检索增强生成", "什么是RAG");
    }
}

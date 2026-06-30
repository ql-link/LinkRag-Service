package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.ChatMessage;
import java.util.Collection;
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
 * 对话轮次落库单测：按 turn_id upsert（起点插行 / 终态更新同行）、状态不回退、幂等、归属校验。
 *
 * <p>LINK-191 起本通道只持久化对话内容，不再写 {@code llm_usage_log}（generate 用量改走 usage_report 通道），
 * 故不再断言用量账本。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChatTurnPersistenceServiceImplTest {

    @Mock
    private ChatConversationMapper conversationMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @InjectMocks
    private ChatTurnPersistenceServiceImpl service;

    /** 单元测试无 MyBatis 启动扫描，手动初始化 MP TableInfo，使 LambdaQueryWrapper 能解析列。 */
    @BeforeAll
    static void initMpTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChatMessage.class);
        TableInfoHelper.initTableInfo(assistant, ChatConversation.class);
    }

    private ChatTurnMQ.MsgPayload payload(String status) {
        ChatTurnMQ.MsgPayload p = new ChatTurnMQ.MsgPayload();
        p.setConversationId(100L);
        p.setTurnId("turn-1");
        p.setRequestId("req-1");
        p.setUserId(42L);
        p.setQuery("什么是RAG");
        p.setAnswer("RAG 是检索增强生成");
        p.setTitle("RAG 入门");
        p.setConfigId(7L);
        p.setProviderType("openai");
        p.setModelName("gpt-4");
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaUpdateWrapper<ChatConversation> captureConversationUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(conversationMapper).update(any(), updateCaptor.capture());
        return updateCaptor.getValue();
    }

    private Collection<Object> conversationUpdateValues() {
        return captureConversationUpdate().getParamNameValuePairs().values();
    }

    private void assertForcesUpdatedAt() {
        assertThat(captureConversationUpdate().getSqlSet()).contains("updated_at = CURRENT_TIMESTAMP");
    }

    @Test
    @DisplayName("Should_InsertRowAndConversation_When_FreshCompleted")
    void Should_PersistAll_When_FreshCompleted() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(null);

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

        LambdaUpdateWrapper<ChatConversation> updateWrapper = captureConversationUpdate();
        assertThat(updateWrapper.getSqlSet()).contains("updated_at = CURRENT_TIMESTAMP");
        assertThat(updateWrapper.getParamNameValuePairs().values())
                .contains(7L, "gpt-4", payload("COMPLETED").getTitle());
    }

    @Test
    @DisplayName("Should_InsertGeneratingRowWithoutTitleGen_When_FreshGenerating")
    void Should_InsertGeneratingRow_When_FreshGenerating() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(null);

        ChatTurnMQ.MsgPayload p = payload("GENERATING");
        p.setAnswer("");
        p.setTitle(null);

        service.persist(p);

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getStatus()).isEqualTo("GENERATING");
        assertForcesUpdatedAt();
    }

    @Test
    @DisplayName("Should_PersistErrorFields_When_Failed")
    void Should_Persist_When_Failed() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(null);

        ChatTurnMQ.MsgPayload p = payload("FAILED");
        p.setAnswer("");
        p.setTitle(null);
        p.setErrorCode("GENERATION_TIMEOUT");
        p.setErrorMessage("timed out");

        service.persist(p);

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(msgCaptor.getValue().getAnswer()).isEmpty();
        assertThat(msgCaptor.getValue().getErrorCode()).isEqualTo("GENERATION_TIMEOUT");
        assertThat(msgCaptor.getValue().getErrorMessage()).isEqualTo("timed out");
    }

    @Test
    @DisplayName("Should_UpdateSameRow_When_GeneratingPromotedToCompleted")
    void Should_PromoteSameRow_When_GeneratingToCompleted() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("GENERATING"));

        service.persist(payload("COMPLETED"));

        // 更新同一行（不再插入新行）。
        verify(messageMapper, never()).insert(any());
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper).updateById(msgCaptor.capture());
        ChatMessage updated = msgCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(555L);
        assertThat(updated.getStatus()).isEqualTo("COMPLETED");
        assertThat(updated.getAnswer()).isEqualTo("RAG 是检索增强生成");
    }

    @Test
    @DisplayName("Should_NotRegress_When_LateGeneratingArrivesAfterTerminal")
    void Should_NotRegress_When_LateGeneratingAfterTerminal() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("COMPLETED"));

        service.persist(payload("GENERATING"));

        verify(messageMapper, never()).insert(any());
        verify(messageMapper, never()).updateById(any());
        verify(conversationMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("Should_SkipDuplicateTerminal_When_TerminalRedelivered")
    void Should_Skip_When_TerminalRedelivered() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("COMPLETED"));

        service.persist(payload("COMPLETED"));

        verify(messageMapper, never()).insert(any());
        verify(messageMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_SkipDuplicateGenerating_When_GeneratingRedelivered")
    void Should_Skip_When_GeneratingRedelivered() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(existingMessage("GENERATING"));

        service.persist(payload("GENERATING"));

        verify(messageMapper, never()).insert(any());
        verify(messageMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_DropAndNotPersist_When_ConversationNotOwnedByUser")
    void Should_Drop_When_OwnershipMismatch() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 999L));

        service.persist(payload("COMPLETED"));

        verify(messageMapper, never()).selectOne(any());
        verify(messageMapper, never()).insert(any());
        verify(conversationMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("Should_DropAndNotPersist_When_ConversationMissing")
    void Should_Drop_When_ConversationMissing() {
        given(conversationMapper.selectOne(any())).willReturn(null);

        service.persist(payload("COMPLETED"));

        verify(messageMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should_NotOverrideManualTitle_When_PythonTitleArrives")
    void Should_NotOverrideManualTitle_When_PythonTitleArrives() {
        ChatConversation manual = conversation(100L, 42L);
        manual.setTitle("用户手动标题");
        given(conversationMapper.selectOne(any())).willReturn(manual);
        given(messageMapper.selectOne(any())).willReturn(null);

        service.persist(payload("COMPLETED"));

        assertThat(conversationUpdateValues()).doesNotContain(payload("COMPLETED").getTitle());
    }

    @Test
    @DisplayName("Should_UpdateTitle_When_CurrentTitleEqualsIncomingTitle")
    void Should_UpdateTitle_When_CurrentTitleEqualsIncomingTitle() {
        ChatConversation sameTitle = conversation(100L, 42L);
        sameTitle.setTitle("RAG 入门");
        given(conversationMapper.selectOne(any())).willReturn(sameTitle);
        given(messageMapper.selectOne(any())).willReturn(null);

        service.persist(payload("COMPLETED"));

        assertThat(conversationUpdateValues()).contains(payload("COMPLETED").getTitle());
    }

    @Test
    @DisplayName("Should_TruncatePythonTitle_When_TitleExceedsColumnLimit")
    void Should_TruncatePythonTitle_When_TitleExceedsColumnLimit() {
        given(conversationMapper.selectOne(any())).willReturn(conversation(100L, 42L));
        given(messageMapper.selectOne(any())).willReturn(null);
        ChatTurnMQ.MsgPayload p = payload("COMPLETED");
        p.setTitle("A".repeat(300));

        service.persist(p);

        assertThat(conversationUpdateValues()).anySatisfy(value -> assertThat(value).asString().hasSize(255));
    }
}

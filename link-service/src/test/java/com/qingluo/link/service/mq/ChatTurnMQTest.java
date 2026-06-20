package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.components.mq.model.ChatTurnMQ;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ChatTurnMQ 反序列化与校验单测。
 *
 * <p>线格式为 Python 端统一信封 {@code {"mq_type","mq_name","payload":{...}}}，
 * 业务字段在 payload 内、全 snake_case；解析需解包 payload。</p>
 */
class ChatTurnMQTest {

    private static final String ENVELOPE = "{"
            + "\"mq_type\":\"CHAT_TURN\",\"mq_name\":\"tolink.rag.chat_turn\",\"payload\":{"
            + "\"message_id\":\"abc123\",\"timestamp\":1718818000.1234,"
            + "\"conversation_id\":10086,\"request_id\":\"req-1\",\"user_id\":42,"
            + "\"query\":\"什么是RAG\",\"answer\":\"RAG 是检索增强生成\","
            + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
            + "\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200,"
            + "\"references\":[\"chunk-1\",\"chunk-2\"],\"latency_ms\":350,\"status\":\"success\"}}";

    @Test
    void Should_UnwrapEnvelopeAndMapSnakeCaseFields() {
        ChatTurnMQ.MsgPayload payload = ChatTurnMQ.parseMsg(ENVELOPE);

        assertThat(ChatTurnMQ.MQ_NAME).isEqualTo("tolink.rag.chat_turn");
        assertThat(payload.getConversationId()).isEqualTo(10086L);
        assertThat(payload.getUserId()).isEqualTo(42L);
        assertThat(payload.getRequestId()).isEqualTo("req-1");
        assertThat(payload.getQuery()).isEqualTo("什么是RAG");
        assertThat(payload.getAnswer()).isEqualTo("RAG 是检索增强生成");
        assertThat(payload.getConfigId()).isEqualTo(7L);
        assertThat(payload.getProviderType()).isEqualTo("openai");
        assertThat(payload.getModelName()).isEqualTo("gpt-4");
        assertThat(payload.getPromptTokens()).isEqualTo(120);
        assertThat(payload.getCompletionTokens()).isEqualTo(80);
        assertThat(payload.getTotalTokens()).isEqualTo(200);
        assertThat(payload.getReferences()).containsExactly("chunk-1", "chunk-2");
        assertThat(payload.getLatencyMs()).isEqualTo(350);
        assertThat(payload.getStatus()).isEqualTo("success");
    }

    @Test
    void Should_ParseFailedStatusWithEmptyAnswer() {
        String raw = "{\"mq_type\":\"CHAT_TURN\",\"mq_name\":\"tolink.rag.chat_turn\",\"payload\":{"
                + "\"conversation_id\":1,\"request_id\":\"req-f\",\"user_id\":2,\"query\":\"q\","
                + "\"answer\":\"\",\"config_id\":3,\"provider_type\":\"openai\",\"model_name\":\"\","
                + "\"status\":\"failed\"}}";

        ChatTurnMQ.MsgPayload payload = ChatTurnMQ.parseMsg(raw);

        assertThat(payload.getStatus()).isEqualTo("failed");
        assertThat(payload.getAnswer()).isEmpty();
        assertThat(payload.getReferences()).isNull();
    }

    @Test
    void Should_RejectInvalidStatus() {
        String raw = "{\"payload\":{\"conversation_id\":1,\"request_id\":\"r\",\"user_id\":2,\"status\":\"done\"}}";

        assertThatThrownBy(() -> ChatTurnMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status is invalid");
    }

    @Test
    void Should_RejectMissingRequestId() {
        String raw = "{\"payload\":{\"conversation_id\":1,\"user_id\":2,\"status\":\"success\"}}";

        assertThatThrownBy(() -> ChatTurnMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request_id is missing");
    }

    @Test
    void Should_SerializeRoundTripForOutboundUse() {
        ChatTurnMQ.MsgPayload payload = new ChatTurnMQ.MsgPayload();
        payload.setConversationId(5L);
        payload.setUserId(6L);
        payload.setRequestId("req-2");
        payload.setStatus("partial");
        payload.setReferences(List.of("c1"));

        ChatTurnMQ mq = new ChatTurnMQ(payload);
        ChatTurnMQ.MsgPayload parsed = ChatTurnMQ.parseMsg(mq.getMessage());

        assertThat(parsed.getConversationId()).isEqualTo(5L);
        assertThat(parsed.getStatus()).isEqualTo("partial");
        assertThat(parsed.getReferences()).containsExactly("c1");
    }
}

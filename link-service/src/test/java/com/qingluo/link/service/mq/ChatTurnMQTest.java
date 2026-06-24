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
            + "\"conversation_id\":10086,\"turn_id\":\"turn-1\",\"request_id\":\"req-1\",\"user_id\":42,"
            + "\"query\":\"什么是RAG\",\"answer\":\"RAG 是检索增强生成\","
            + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
            + "\"references\":[\"chunk-1\",\"chunk-2\"],\"latency_ms\":350,\"status\":\"COMPLETED\"}}";

    @Test
    void Should_UnwrapEnvelopeAndMapSnakeCaseFields() {
        ChatTurnMQ.MsgPayload payload = ChatTurnMQ.parseMsg(ENVELOPE);

        assertThat(ChatTurnMQ.MQ_NAME).isEqualTo("tolink.rag.chat_turn");
        assertThat(payload.getConversationId()).isEqualTo(10086L);
        assertThat(payload.getTurnId()).isEqualTo("turn-1");
        assertThat(payload.getUserId()).isEqualTo(42L);
        assertThat(payload.getRequestId()).isEqualTo("req-1");
        assertThat(payload.getQuery()).isEqualTo("什么是RAG");
        assertThat(payload.getAnswer()).isEqualTo("RAG 是检索增强生成");
        assertThat(payload.getConfigId()).isEqualTo(7L);
        assertThat(payload.getProviderType()).isEqualTo("openai");
        assertThat(payload.getModelName()).isEqualTo("gpt-4");
        assertThat(payload.getReferences()).containsExactly("chunk-1", "chunk-2");
        assertThat(payload.getLatencyMs()).isEqualTo(350);
        assertThat(payload.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void Should_ParseGeneratingStartWithEmptyProviderAndAnswer() {
        String raw = "{\"mq_type\":\"CHAT_TURN\",\"mq_name\":\"tolink.rag.chat_turn\",\"payload\":{"
                + "\"conversation_id\":1,\"turn_id\":\"turn-g\",\"request_id\":\"req-g\",\"user_id\":2,"
                + "\"query\":\"q\",\"answer\":\"\",\"provider_type\":\"\",\"status\":\"GENERATING\"}}";

        ChatTurnMQ.MsgPayload payload = ChatTurnMQ.parseMsg(raw);

        assertThat(payload.getStatus()).isEqualTo("GENERATING");
        assertThat(payload.getProviderType()).isEmpty();
        assertThat(payload.getAnswer()).isEmpty();
    }

    @Test
    void Should_ParseFailedStatusWithErrorFields() {
        String raw = "{\"mq_type\":\"CHAT_TURN\",\"mq_name\":\"tolink.rag.chat_turn\",\"payload\":{"
                + "\"conversation_id\":1,\"turn_id\":\"turn-f\",\"request_id\":\"req-f\",\"user_id\":2,\"query\":\"q\","
                + "\"answer\":\"\",\"config_id\":3,\"provider_type\":\"openai\",\"model_name\":\"\","
                + "\"status\":\"FAILED\",\"error_code\":\"GENERATION_TIMEOUT\",\"error_message\":\"timed out\"}}";

        ChatTurnMQ.MsgPayload payload = ChatTurnMQ.parseMsg(raw);

        assertThat(payload.getStatus()).isEqualTo("FAILED");
        assertThat(payload.getAnswer()).isEmpty();
        assertThat(payload.getReferences()).isNull();
        assertThat(payload.getErrorCode()).isEqualTo("GENERATION_TIMEOUT");
        assertThat(payload.getErrorMessage()).isEqualTo("timed out");
    }

    @Test
    void Should_RejectInvalidStatus() {
        String raw = "{\"payload\":{\"conversation_id\":1,\"turn_id\":\"t\",\"request_id\":\"r\",\"user_id\":2,\"status\":\"success\"}}";

        assertThatThrownBy(() -> ChatTurnMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status is invalid");
    }

    @Test
    void Should_RejectMissingTurnId() {
        String raw = "{\"payload\":{\"conversation_id\":1,\"request_id\":\"r\",\"user_id\":2,\"status\":\"COMPLETED\"}}";

        assertThatThrownBy(() -> ChatTurnMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("turn_id is missing");
    }

    @Test
    void Should_RejectMissingRequestId() {
        String raw = "{\"payload\":{\"conversation_id\":1,\"turn_id\":\"t\",\"user_id\":2,\"status\":\"COMPLETED\"}}";

        assertThatThrownBy(() -> ChatTurnMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request_id is missing");
    }

    @Test
    void Should_SerializeRoundTripForOutboundUse() {
        ChatTurnMQ.MsgPayload payload = new ChatTurnMQ.MsgPayload();
        payload.setConversationId(5L);
        payload.setTurnId("turn-2");
        payload.setUserId(6L);
        payload.setRequestId("req-2");
        payload.setStatus("GENERATING");
        payload.setReferences(List.of("c1"));

        ChatTurnMQ mq = new ChatTurnMQ(payload);
        ChatTurnMQ.MsgPayload parsed = ChatTurnMQ.parseMsg(mq.getMessage());

        assertThat(parsed.getConversationId()).isEqualTo(5L);
        assertThat(parsed.getTurnId()).isEqualTo("turn-2");
        assertThat(parsed.getStatus()).isEqualTo("GENERATING");
        assertThat(parsed.getReferences()).containsExactly("c1");
    }
}

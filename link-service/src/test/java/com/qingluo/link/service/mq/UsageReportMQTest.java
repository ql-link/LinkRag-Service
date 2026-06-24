package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.components.mq.model.UsageReportMQ;
import org.junit.jupiter.api.Test;

/**
 * UsageReportMQ 反序列化与校验单测。
 *
 * <p>线格式为 Python 端统一信封 {@code {"mq_type","mq_name","payload":{...}}}，
 * 业务字段在 payload 内、全 snake_case；解析需解包 payload。</p>
 */
class UsageReportMQTest {

    private static final String PARSE_EMBED = "{"
            + "\"mq_type\":\"USAGE_REPORT\",\"mq_name\":\"tolink.rag.usage_report\",\"payload\":{"
            + "\"message_id\":\"a1b2c3d4\",\"timestamp\":1718900000.123,"
            + "\"user_id\":\"1001\",\"provider_type\":\"openai\",\"model_name\":\"text-embedding-3-large\","
            + "\"stage\":\"parse\",\"operation\":\"embed\",\"prompt_tokens\":12840,\"completion_tokens\":0,"
            + "\"total_tokens\":12840,\"config_id\":555,\"task_id\":\"task-20260620-001\",\"status\":\"success\"}}";

    @Test
    void Should_UnwrapEnvelopeAndMapSnakeCaseFields() {
        UsageReportMQ.MsgPayload payload = UsageReportMQ.parseMsg(PARSE_EMBED);

        assertThat(UsageReportMQ.MQ_NAME).isEqualTo("tolink.rag.usage_report");
        // user_id Python 以 string 传，Java 端反序列化为 Long
        assertThat(payload.getUserId()).isEqualTo(1001L);
        assertThat(payload.getProviderType()).isEqualTo("openai");
        assertThat(payload.getModelName()).isEqualTo("text-embedding-3-large");
        assertThat(payload.getStage()).isEqualTo("parse");
        assertThat(payload.getOperation()).isEqualTo("embed");
        assertThat(payload.getPromptTokens()).isEqualTo(12840);
        assertThat(payload.getCompletionTokens()).isZero();
        assertThat(payload.getTotalTokens()).isEqualTo(12840);
        assertThat(payload.getConfigId()).isEqualTo(555L);
        assertThat(payload.getTaskId()).isEqualTo("task-20260620-001");
        assertThat(payload.getStatus()).isEqualTo("success");
    }

    @Test
    void Should_LeaveOptionalFieldsNull_When_RecallSystemConfig() {
        String raw = "{\"mq_type\":\"USAGE_REPORT\",\"mq_name\":\"tolink.rag.usage_report\",\"payload\":{"
                + "\"user_id\":1001,\"provider_type\":\"openai\",\"model_name\":\"text-embedding-3-large\","
                + "\"stage\":\"recall\",\"operation\":\"embed\",\"prompt_tokens\":36,\"completion_tokens\":0,"
                + "\"total_tokens\":36,\"status\":\"success\"}}";

        UsageReportMQ.MsgPayload payload = UsageReportMQ.parseMsg(raw);

        // 系统配置调用：config_id 缺省 → NULL；latency_ms / task_id 同样缺省 → NULL
        assertThat(payload.getConfigId()).isNull();
        assertThat(payload.getLatencyMs()).isNull();
        assertThat(payload.getTaskId()).isNull();
    }

    @Test
    void Should_IgnoreLegacyConversationAndRequestFields_When_StillSentByUpstream() {
        // 瘦身后 conversation_id/request_id 已移出契约，旧上游若仍发也应被忽略（不抛错）。
        String raw = "{\"payload\":{\"user_id\":1001,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"stage\":\"chat\",\"operation\":\"generate\",\"prompt_tokens\":120,\"completion_tokens\":80,"
                + "\"total_tokens\":200,\"conversation_id\":7788,\"request_id\":\"req-legacy-1\",\"status\":\"success\"}}";

        UsageReportMQ.MsgPayload payload = UsageReportMQ.parseMsg(raw);

        assertThat(payload.getStage()).isEqualTo("chat");
        assertThat(payload.getOperation()).isEqualTo("generate");
        assertThat(payload.getTotalTokens()).isEqualTo(200);
    }

    @Test
    void Should_ParseVisionWithRealCompletionTokens() {
        String raw = "{\"payload\":{\"user_id\":1001,\"provider_type\":\"openai\",\"model_name\":\"gpt-4o\","
                + "\"stage\":\"parse\",\"operation\":\"vision\",\"prompt_tokens\":2400,\"completion_tokens\":380,"
                + "\"total_tokens\":2780,\"config_id\":555,\"status\":\"success\"}}";

        UsageReportMQ.MsgPayload payload = UsageReportMQ.parseMsg(raw);

        assertThat(payload.getOperation()).isEqualTo("vision");
        assertThat(payload.getCompletionTokens()).isEqualTo(380);
    }

    @Test
    void Should_RejectInvalidStage() {
        String raw = "{\"payload\":{\"user_id\":1,\"provider_type\":\"openai\",\"model_name\":\"m\","
                + "\"stage\":\"unknown\",\"operation\":\"embed\",\"prompt_tokens\":1,\"completion_tokens\":0,"
                + "\"total_tokens\":1}}";

        assertThatThrownBy(() -> UsageReportMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage is invalid");
    }

    @Test
    void Should_RejectInvalidOperation() {
        String raw = "{\"payload\":{\"user_id\":1,\"provider_type\":\"openai\",\"model_name\":\"m\","
                + "\"stage\":\"recall\",\"operation\":\"sparse\",\"prompt_tokens\":1,\"completion_tokens\":0,"
                + "\"total_tokens\":1}}";

        assertThatThrownBy(() -> UsageReportMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operation is invalid");
    }

    @Test
    void Should_RejectMissingTokens() {
        String raw = "{\"payload\":{\"user_id\":1,\"provider_type\":\"openai\",\"model_name\":\"m\","
                + "\"stage\":\"recall\",\"operation\":\"embed\"}}";

        assertThatThrownBy(() -> UsageReportMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token fields are missing");
    }

    @Test
    void Should_RejectMissingUserId() {
        String raw = "{\"payload\":{\"provider_type\":\"openai\",\"model_name\":\"m\","
                + "\"stage\":\"recall\",\"operation\":\"embed\",\"prompt_tokens\":1,\"completion_tokens\":0,"
                + "\"total_tokens\":1}}";

        assertThatThrownBy(() -> UsageReportMQ.parseMsg(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user_id is missing");
    }

    @Test
    void Should_SerializeRoundTripForOutboundUse() {
        UsageReportMQ.MsgPayload payload = new UsageReportMQ.MsgPayload();
        payload.setUserId(6L);
        payload.setProviderType("jina");
        payload.setModelName("jina-reranker-v2");
        payload.setStage("recall");
        payload.setOperation("rerank");
        payload.setPromptTokens(1820);
        payload.setCompletionTokens(0);
        payload.setTotalTokens(1820);
        payload.setConfigId(556L);
        payload.setStatus("success");

        UsageReportMQ mq = new UsageReportMQ(payload);
        UsageReportMQ.MsgPayload parsed = UsageReportMQ.parseMsg(mq.getMessage());

        assertThat(parsed.getOperation()).isEqualTo("rerank");
        assertThat(parsed.getConfigId()).isEqualTo(556L);
        assertThat(parsed.getTotalTokens()).isEqualTo(1820);
    }
}

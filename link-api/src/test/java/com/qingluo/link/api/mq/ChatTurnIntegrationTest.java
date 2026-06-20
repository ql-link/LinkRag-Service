package com.qingluo.link.api.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.model.dto.entity.ChatMessage;
import com.qingluo.link.service.mq.ChatTurnKafkaReceiver;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 对话轮次落库端到端集成测试。
 *
 * <p>尽可能贴近真实链路：用 Python 端真实线格式（统一信封、snake_case、routing_key=conversation_id）
 * 喂给生产代码的 {@link ChatTurnKafkaReceiver}，经
 * {@code ChatTurnMQ.parseMsg → ChatTurnConsumer → ChatTurnPersistenceService → Mapper}
 * 真实写入 H2，断言 chat_message / llm_usage_log / chat_conversation 三表数据。</p>
 *
 * <p>覆盖：success / partial / failed 三类落库、references JSON 读写往返、
 * request_id 幂等去重、conversation 归属校验拒绝跨用户写入。</p>
 */
@SpringBootTest(properties = {
    "tolink.mq.vender=kafka",
    "tolink.mq.kafka-auto-create-topics=false",
    "spring.kafka.bootstrap-servers=127.0.0.1:19092",
    "spring.kafka.listener.auto-startup=false"
})
@Import(TestSecurityConfig.class)
class ChatTurnIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ChatTurnKafkaReceiver receiver;
    @Autowired private ChatMessageMapper chatMessageMapper;

    private Long userId;
    private Long otherUserId;
    private Long datasetId;
    private Long conversationId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM llm_usage_log");
        jdbcTemplate.update("DELETE FROM chat_message");
        jdbcTemplate.update("DELETE FROM chat_conversation");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        jdbcTemplate.update("""
            INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
            VALUES ('turn_user', 'password', '对话用户', 'turn@test.com', 'USER', 1)
            """);
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = 'turn_user'", Long.class);
        jdbcTemplate.update("""
            INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
            VALUES ('other_user', 'password', '他人', 'other@test.com', 'USER', 1)
            """);
        otherUserId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = 'other_user'", Long.class);

        jdbcTemplate.update("INSERT INTO dataset (user_id, name, status) VALUES (?, 'turn_dataset', 'ACTIVE')", userId);
        datasetId = jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE name = 'turn_dataset'", Long.class);

        jdbcTemplate.update("""
            INSERT INTO chat_conversation (user_id, dataset_id, title, is_pinned)
            VALUES (?, ?, '新对话', FALSE)
            """, userId, datasetId);
        conversationId = jdbcTemplate.queryForObject(
            "SELECT id FROM chat_conversation WHERE user_id = ?", Long.class, userId);
    }

    /** 构造 Python 端真实线格式：统一信封 + snake_case payload。 */
    private String envelope(String payloadBody) {
        return "{\"mq_type\":\"CHAT_TURN\",\"mq_name\":\"tolink.rag.chat_turn\",\"payload\":{"
                + "\"message_id\":\"" + java.util.UUID.randomUUID().toString().replace("-", "") + "\","
                + "\"timestamp\":1718818000.1234,"
                + payloadBody + "}}";
    }

    @Test
    @DisplayName("success：三表正确落库，references JSON 读写往返，标题由首条提问生成")
    void Should_PersistAllThreeTables_When_SuccessTurn() {
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"request_id\":\"req-success-1\",\"user_id\":" + userId + ","
                + "\"query\":\"什么是RAG\",\"answer\":\"RAG 是检索增强生成，结合检索与大模型。\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"prompt_tokens\":120,\"completion_tokens\":80,\"total_tokens\":200,"
                + "\"references\":[\"chunk-1\",\"chunk-2\",\"chunk-3\"],\"latency_ms\":1350,\"status\":\"success\"");

        receiver.receive(raw);

        // chat_message：一行一轮
        Map<String, Object> msg = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE request_id = 'req-success-1'");
        assertThat(msg.get("conversation_id")).isEqualTo(conversationId);
        assertThat(msg.get("query")).isEqualTo("什么是RAG");
        assertThat(msg.get("answer")).isEqualTo("RAG 是检索增强生成，结合检索与大模型。");
        assertThat(msg.get("config_id")).isEqualTo(7L);
        assertThat(msg.get("model_name")).isEqualTo("gpt-4");
        assertThat(msg.get("status")).isEqualTo("success");

        Long messageId = ((Number) msg.get("id")).longValue();

        // references JSON 读路径往返（autoResultMap + JacksonTypeHandler）
        ChatMessage loaded = chatMessageMapper.selectById(messageId);
        assertThat(loaded.getReferences()).containsExactly("chunk-1", "chunk-2", "chunk-3");

        // llm_usage_log：关联 conversation_id / message_id / request_id
        Map<String, Object> usage = jdbcTemplate.queryForMap(
                "SELECT * FROM llm_usage_log WHERE request_id = 'req-success-1'");
        assertThat(usage.get("user_id")).isEqualTo(userId);
        assertThat(usage.get("conversation_id")).isEqualTo(conversationId);
        assertThat(((Number) usage.get("message_id")).longValue()).isEqualTo(messageId);
        assertThat(usage.get("provider_type")).isEqualTo("openai");
        assertThat(usage.get("model_name")).isEqualTo("gpt-4");
        assertThat(usage.get("prompt_tokens")).isEqualTo(120);
        assertThat(usage.get("completion_tokens")).isEqualTo(80);
        assertThat(usage.get("total_tokens")).isEqualTo(200);
        assertThat(usage.get("latency_ms")).isEqualTo(1350);
        assertThat(usage.get("status")).isEqualTo("success");

        // chat_conversation：last_config_id / last_model_name 更新，首轮标题由提问生成
        Map<String, Object> conv = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_conversation WHERE id = ?", conversationId);
        assertThat(conv.get("last_config_id")).isEqualTo(7L);
        assertThat(conv.get("last_model_name")).isEqualTo("gpt-4");
        assertThat(conv.get("title")).isEqualTo("什么是RAG");
    }

    @Test
    @DisplayName("partial：客户端断连，半截 answer 落库")
    void Should_PersistHalfAnswer_When_PartialTurn() {
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"request_id\":\"req-partial-1\",\"user_id\":" + userId + ","
                + "\"query\":\"讲讲向量检索\",\"answer\":\"向量检索是\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"prompt_tokens\":50,\"completion_tokens\":5,\"total_tokens\":55,"
                + "\"references\":[\"chunk-9\"],\"latency_ms\":200,\"status\":\"partial\"");

        receiver.receive(raw);

        Map<String, Object> msg = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE request_id = 'req-partial-1'");
        assertThat(msg.get("status")).isEqualTo("partial");
        assertThat(msg.get("answer")).isEqualTo("向量检索是");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM llm_usage_log WHERE request_id = 'req-partial-1'", String.class))
                .isEqualTo("partial");
    }

    @Test
    @DisplayName("failed：生成异常，answer 空串、token 为 0 仍落库")
    void Should_PersistEmptyAnswer_When_FailedTurn() {
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"request_id\":\"req-failed-1\",\"user_id\":" + userId + ","
                + "\"query\":\"会失败的问题\",\"answer\":\"\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"prompt_tokens\":0,\"completion_tokens\":0,\"total_tokens\":0,"
                + "\"references\":[],\"latency_ms\":null,\"status\":\"failed\"");

        receiver.receive(raw);

        Map<String, Object> msg = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE request_id = 'req-failed-1'");
        assertThat(msg.get("status")).isEqualTo("failed");
        assertThat(msg.get("answer")).isEqualTo("");
        Map<String, Object> usage = jdbcTemplate.queryForMap(
                "SELECT * FROM llm_usage_log WHERE request_id = 'req-failed-1'");
        assertThat(usage.get("total_tokens")).isEqualTo(0);
        assertThat(usage.get("latency_ms")).isNull();
    }

    @Test
    @DisplayName("幂等：相同 request_id 重投，只落一行，不重复")
    void Should_NotDuplicate_When_SameRequestIdRedelivered() {
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"request_id\":\"req-idem-1\",\"user_id\":" + userId + ","
                + "\"query\":\"重复消息\",\"answer\":\"回答\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"prompt_tokens\":10,\"completion_tokens\":10,\"total_tokens\":20,"
                + "\"references\":[],\"latency_ms\":100,\"status\":\"success\"");

        receiver.receive(raw);
        receiver.receive(raw);
        receiver.receive(raw);

        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE request_id = 'req-idem-1'", Integer.class);
        Integer usageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_usage_log WHERE request_id = 'req-idem-1'", Integer.class);
        assertThat(msgCount).isEqualTo(1);
        assertThat(usageCount).isEqualTo(1);
    }

    @Test
    @DisplayName("归属校验：conversation 不属于该 user，丢弃不落库")
    void Should_DropAndNotPersist_When_ConversationNotOwnedByUser() {
        // user_id 为他人，但 conversation 属于 userId → 跨用户写入应被拒绝
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"request_id\":\"req-cross-1\",\"user_id\":" + otherUserId + ","
                + "\"query\":\"越权写入\",\"answer\":\"不该落库\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"prompt_tokens\":10,\"completion_tokens\":10,\"total_tokens\":20,"
                + "\"references\":[],\"latency_ms\":100,\"status\":\"success\"");

        receiver.receive(raw);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE request_id = 'req-cross-1'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_usage_log WHERE request_id = 'req-cross-1'", Integer.class)).isZero();
        // 对话标题保持默认，未被越权消息改写
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM chat_conversation WHERE id = ?", String.class, conversationId))
                .isEqualTo("新对话");
    }
}

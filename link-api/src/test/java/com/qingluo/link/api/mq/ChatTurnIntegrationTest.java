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
 * 真实写入 H2，断言 chat_message / chat_conversation 两表数据。</p>
 *
 * <p>覆盖：GENERATING 起点插行 → COMPLETED 终态按 turn_id upsert 同一行、FAILED 错误字段落库、
 * references JSON 读写往返、turn_id 幂等去重、状态不回退、conversation 归属校验拒绝跨用户写入。</p>
 *
 * <p>LINK-191 起本通道<b>不再写 {@code llm_usage_log}</b>（generate 用量改走 usage_report 通道），
 * 故每个用例都断言 {@code llm_usage_log} 维持为空。</p>
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

    /** chat_turn 通道不再写用量账本，全表应始终为空。 */
    private int usageRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_usage_log", Integer.class);
    }

    @Test
    @DisplayName("GENERATING→COMPLETED：起点插「生成中」行，终态按 turn_id upsert 同一行，不写用量账本")
    void Should_UpsertSameRow_When_GeneratingThenCompleted() {
        String turnId = "turn-up-1";
        // 1) 起点：GENERATING，answer 空、provider_type 空。
        receiver.receive(envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"" + turnId + "\","
                + "\"request_id\":\"req-gen-1\",\"user_id\":" + userId + ","
                + "\"query\":\"什么是RAG\",\"answer\":\"\",\"provider_type\":\"\",\"status\":\"GENERATING\""));

        Map<String, Object> generating = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE turn_id = ?", turnId);
        assertThat(generating.get("status")).isEqualTo("GENERATING");
        assertThat(generating.get("answer")).isEqualTo("");
        Long messageId = ((Number) generating.get("id")).longValue();
        // chat_turn 不写用量账本
        assertThat(usageRowCount()).isZero();

        // 2) 终态：COMPLETED，同 turn_id 更新同一行。
        receiver.receive(envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"" + turnId + "\","
                + "\"request_id\":\"req-gen-1\",\"user_id\":" + userId + ","
                + "\"query\":\"什么是RAG\",\"answer\":\"RAG 是检索增强生成，结合检索与大模型。\","
                + "\"title\":\"RAG 入门\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"references\":[\"chunk-1\",\"chunk-2\",\"chunk-3\"],\"latency_ms\":1350,\"status\":\"COMPLETED\""));

        // 仍是一行，id 不变，状态推进为 COMPLETED，answer 补齐。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE turn_id = ?", Integer.class, turnId)).isEqualTo(1);
        Map<String, Object> completed = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE turn_id = ?", turnId);
        assertThat(((Number) completed.get("id")).longValue()).isEqualTo(messageId);
        assertThat(completed.get("status")).isEqualTo("COMPLETED");
        assertThat(completed.get("answer")).isEqualTo("RAG 是检索增强生成，结合检索与大模型。");
        assertThat(completed.get("model_name")).isEqualTo("gpt-4");

        // references JSON 读路径往返（autoResultMap + JacksonTypeHandler）
        ChatMessage loaded = chatMessageMapper.selectById(messageId);
        assertThat(loaded.getReferences()).containsExactly("chunk-1", "chunk-2", "chunk-3");

        // 终态仍不写用量账本（generate 用量改走 usage_report 通道）。
        assertThat(usageRowCount()).isZero();

        Map<String, Object> conv = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_conversation WHERE id = ?", conversationId);
        assertThat(conv.get("last_config_id")).isEqualTo(7L);
        assertThat(conv.get("last_model_name")).isEqualTo("gpt-4");
        assertThat(conv.get("title")).isEqualTo("RAG 入门");
    }

    @Test
    @DisplayName("COMPLETED：终态直达（GENERATING 丢失）也能落库，且不写用量账本")
    void Should_PersistChatMessage_When_CompletedTurn() {
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"turn-c-1\","
                + "\"request_id\":\"req-success-1\",\"user_id\":" + userId + ","
                + "\"query\":\"什么是RAG\",\"answer\":\"RAG 是检索增强生成，结合检索与大模型。\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"references\":[\"chunk-1\"],\"latency_ms\":1350,\"status\":\"COMPLETED\"");

        receiver.receive(raw);

        Map<String, Object> msg = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE turn_id = 'turn-c-1'");
        assertThat(msg.get("status")).isEqualTo("COMPLETED");
        assertThat(msg.get("answer")).isEqualTo("RAG 是检索增强生成，结合检索与大模型。");
        assertThat(usageRowCount()).isZero();
    }

    @Test
    @DisplayName("FAILED：生成异常，answer 空串、error_code/error_message 落库，不写用量账本")
    void Should_PersistErrorFields_When_FailedTurn() {
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"turn-f-1\","
                + "\"request_id\":\"req-failed-1\",\"user_id\":" + userId + ","
                + "\"query\":\"会失败的问题\",\"answer\":\"\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"references\":[],\"latency_ms\":null,\"status\":\"FAILED\","
                + "\"error_code\":\"GENERATION_TIMEOUT\",\"error_message\":\"timed out\"");

        receiver.receive(raw);

        Map<String, Object> msg = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE turn_id = 'turn-f-1'");
        assertThat(msg.get("status")).isEqualTo("FAILED");
        assertThat(msg.get("answer")).isEqualTo("");
        assertThat(msg.get("error_code")).isEqualTo("GENERATION_TIMEOUT");
        assertThat(msg.get("error_message")).isEqualTo("timed out");
        assertThat(usageRowCount()).isZero();
    }

    @Test
    @DisplayName("幂等：相同 turn_id 终态重投，只落一行")
    void Should_NotDuplicate_When_SameTurnIdRedelivered() {
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"turn-idem-1\","
                + "\"request_id\":\"req-idem-1\",\"user_id\":" + userId + ","
                + "\"query\":\"重复消息\",\"answer\":\"回答\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"references\":[],\"latency_ms\":100,\"status\":\"COMPLETED\"");

        receiver.receive(raw);
        receiver.receive(raw);
        receiver.receive(raw);

        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE turn_id = 'turn-idem-1'", Integer.class);
        assertThat(msgCount).isEqualTo(1);
        assertThat(usageRowCount()).isZero();
    }

    @Test
    @DisplayName("状态不回退：终态后迟到的 GENERATING 不覆盖")
    void Should_NotRegress_When_LateGeneratingAfterTerminal() {
        String turnId = "turn-noreg-1";
        receiver.receive(envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"" + turnId + "\","
                + "\"request_id\":\"req-noreg-1\",\"user_id\":" + userId + ","
                + "\"query\":\"问题\",\"answer\":\"完整回答\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"references\":[],\"latency_ms\":100,\"status\":\"COMPLETED\""));

        // 迟到的 GENERATING（同 turn_id）应被忽略，不回退状态、不清空 answer。
        receiver.receive(envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"" + turnId + "\","
                + "\"request_id\":\"req-noreg-1\",\"user_id\":" + userId + ","
                + "\"query\":\"问题\",\"answer\":\"\",\"provider_type\":\"\",\"status\":\"GENERATING\""));

        Map<String, Object> msg = jdbcTemplate.queryForMap(
                "SELECT * FROM chat_message WHERE turn_id = ?", turnId);
        assertThat(msg.get("status")).isEqualTo("COMPLETED");
        assertThat(msg.get("answer")).isEqualTo("完整回答");
        assertThat(usageRowCount()).isZero();
    }

    @Test
    @DisplayName("归属校验：conversation 不属于该 user，丢弃不落库")
    void Should_DropAndNotPersist_When_ConversationNotOwnedByUser() {
        // user_id 为他人，但 conversation 属于 userId → 跨用户写入应被拒绝
        String raw = envelope(
                "\"conversation_id\":" + conversationId + ",\"turn_id\":\"turn-cross-1\","
                + "\"request_id\":\"req-cross-1\",\"user_id\":" + otherUserId + ","
                + "\"query\":\"越权写入\",\"answer\":\"不该落库\","
                + "\"config_id\":7,\"provider_type\":\"openai\",\"model_name\":\"gpt-4\","
                + "\"references\":[],\"latency_ms\":100,\"status\":\"COMPLETED\"");

        receiver.receive(raw);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE turn_id = 'turn-cross-1'", Integer.class)).isZero();
        assertThat(usageRowCount()).isZero();
        // 对话标题保持默认，未被越权消息改写
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM chat_conversation WHERE id = ?", String.class, conversationId))
                .isEqualTo("新对话");
    }
}

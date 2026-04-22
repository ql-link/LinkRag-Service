package com.qingluo.link.api.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.service.mq.KnowledgeParseResultKafkaReceiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "qingluopay.mq.vender=kafka",
    "qingluopay.mq.kafka-auto-create-topics=false",
    "spring.kafka.bootstrap-servers=127.0.0.1:19092",
    "spring.kafka.listener.auto-startup=false"
})
@Import(TestSecurityConfig.class)
class KnowledgeParseResultIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KnowledgeParseResultKafkaReceiver knowledgeParseResultKafkaReceiver;

    private Long userId;
    private Long datasetId;
    private Long documentId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        String username = "mq_user_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
                VALUES (?, 'password', 'MQ测试用户', ?, 'USER', 1)
                """, username, username + "@test.com");
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);

        String datasetName = "mq_dataset_" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO dataset (user_id, name, description, status)
                VALUES (?, ?, 'MQ解析结果测试数据集', 'ACTIVE')
                """, userId, datasetName);
        datasetId = jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE user_id = ? AND name = ?",
            Long.class, userId, datasetName);

        jdbcTemplate.update("""
                INSERT INTO document_original_file (
                    dataset_id, user_id, original_filename, file_suffix, file_size, content_type,
                    bucket_name, object_key, file_url, upload_status, is_upload_success,
                    parse_notice_status, parse_task_id, parse_status, is_parse_success,
                    parse_notice_retry_count
                ) VALUES (?, ?, 'guide.md', 'md', 128, 'text/markdown',
                    'rag-raw', '1/1/2026/04/21/guide.md', 'http://tolink-service:8080/internal/file',
                    'success', TRUE, 'sent', 'task-integration-1', 'pending', FALSE, 0)
                """, datasetId, userId);
        documentId = jdbcTemplate.queryForObject(
            "SELECT id FROM document_original_file WHERE parse_task_id = 'task-integration-1'", Long.class);
    }

    @Test
    void Should_UpdateDatabase_When_ParseResultReceiverConsumesSuccessMessage() {
        knowledgeParseResultKafkaReceiver.receive("""
            {"mq_type":"parse_result","mq_name":"tolink.rag.parse_result","payload":{"task_id":"task-integration-1","document_id":"%d","success":true,"status":"success","parsed_bucket_name":"rag-parsed","parsed_object_key":"parsed/2026/04/21/%d.md","parsed_file_url":"http://rag/%d.md","failure_reason":"","time_cost_ms":123}}
            """.formatted(documentId, documentId, documentId));

        String parseStatus = jdbcTemplate.queryForObject(
            "SELECT parse_status FROM document_original_file WHERE id = ?", String.class, documentId);
        Boolean isParseSuccess = jdbcTemplate.queryForObject(
            "SELECT is_parse_success FROM document_original_file WHERE id = ?", Boolean.class, documentId);
        String parsedBucketName = jdbcTemplate.queryForObject(
            "SELECT parsed_bucket_name FROM document_original_file WHERE id = ?", String.class, documentId);
        String parsedObjectKey = jdbcTemplate.queryForObject(
            "SELECT parsed_object_key FROM document_original_file WHERE id = ?", String.class, documentId);
        String parsedFileUrl = jdbcTemplate.queryForObject(
            "SELECT parsed_file_url FROM document_original_file WHERE id = ?", String.class, documentId);

        assertThat(parseStatus).isEqualTo("success");
        assertThat(isParseSuccess).isTrue();
        assertThat(parsedBucketName).isEqualTo("rag-parsed");
        assertThat(parsedObjectKey).isEqualTo("parsed/2026/04/21/%d.md".formatted(documentId));
        assertThat(parsedFileUrl).isEqualTo("http://rag/%d.md".formatted(documentId));
    }

    @Test
    void Should_UpdateFailureReason_When_ParseResultReceiverConsumesFailedMessage() {
        knowledgeParseResultKafkaReceiver.receive("""
            {"mq_type":"parse_result","mq_name":"tolink.rag.parse_result","payload":{"task_id":"task-integration-1","document_id":"%d","success":false,"status":"failed","parsed_bucket_name":"","parsed_object_key":"","parsed_file_url":"","failure_reason":"vectorize failed","time_cost_ms":321}}
            """.formatted(documentId));

        String parseStatus = jdbcTemplate.queryForObject(
            "SELECT parse_status FROM document_original_file WHERE id = ?", String.class, documentId);
        Boolean isParseSuccess = jdbcTemplate.queryForObject(
            "SELECT is_parse_success FROM document_original_file WHERE id = ?", Boolean.class, documentId);
        String parseFailureReason = jdbcTemplate.queryForObject(
            "SELECT parse_failure_reason FROM document_original_file WHERE id = ?", String.class, documentId);

        assertThat(parseStatus).isEqualTo("failed");
        assertThat(isParseSuccess).isFalse();
        assertThat(parseFailureReason).isEqualTo("vectorize failed");
    }
}

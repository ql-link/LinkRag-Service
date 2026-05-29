package com.qingluo.link.api.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.service.mq.DocumentParseResultKafkaReceiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "tolink.mq.vender=kafka",
    "tolink.mq.kafka-auto-create-topics=false",
    "spring.kafka.bootstrap-servers=127.0.0.1:19092",
    "spring.kafka.listener.auto-startup=false"
})
@Import(TestSecurityConfig.class)
class DocumentParseResultIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DocumentParseResultKafkaReceiver receiver;

    private Long userId;
    private Long datasetId;
    private Long originalFileId;
    private Long parsedLogId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM document_parsed_log");
        jdbcTemplate.update("DELETE FROM document_parse_file");
        jdbcTemplate.update("DELETE FROM document_original_file");
        jdbcTemplate.update("DELETE FROM dataset");
        jdbcTemplate.update("DELETE FROM sys_user");

        jdbcTemplate.update("""
            INSERT INTO sys_user (username, password_hash, nickname, email, role, status)
            VALUES ('mq_user', 'password', 'MQ用户', 'mq@test.com', 'USER', 1)
            """);
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = 'mq_user'", Long.class);
        jdbcTemplate.update("INSERT INTO dataset (user_id, name, status) VALUES (?, 'mq_dataset', 'ACTIVE')", userId);
        datasetId = jdbcTemplate.queryForObject("SELECT id FROM dataset WHERE name = 'mq_dataset'", Long.class);
        jdbcTemplate.update("""
            INSERT INTO document_original_file (
                dataset_id, user_id, original_filename, file_suffix, file_size, bucket_name,
                object_key, upload_status, is_upload_success
            ) VALUES (?, ?, 'guide.md', 'md', 128, 'rag-raw', 'raw/guide.md', 'success', TRUE)
            """, datasetId, userId);
        originalFileId = jdbcTemplate.queryForObject("SELECT id FROM document_original_file", Long.class);
        jdbcTemplate.update("""
            INSERT INTO document_parse_file (
                document_original_file_id, dataset_id, user_id, latest_parse_task_id, original_filename, parse_count
            ) VALUES (?, ?, ?, 'task-integration-1', 'guide.md', 1)
            """, originalFileId, datasetId, userId);
        Long parseFileId = jdbcTemplate.queryForObject("SELECT id FROM document_parse_file", Long.class);
        jdbcTemplate.update("""
            INSERT INTO document_parsed_log (
                task_id, document_original_file_id, document_parse_file_id, trigger_mode, task_status,
                parsed_filename, parsed_bucket_name, parsed_object_key, parse_finished_at
            ) VALUES ('task-integration-1', ?, ?, 'upload_auto', 'success',
                'guide.md', 'rag-md', 'parsed/guide.md', CURRENT_TIMESTAMP)
            """, originalFileId, parseFileId);
        parsedLogId = jdbcTemplate.queryForObject("SELECT id FROM document_parsed_log", Long.class);
    }

    @Test
    void Should_AcceptTerminalResultWithoutRewritingPythonPersistedState() {
        receiver.receive("""
            {"task_id":"task-integration-1","original_file_id":%d,"document_parsed_log_id":%d,"dataset_id":%d,"user_id":%d,"task_status":"success","failure_reason":null,"parse_finished_at":"2026-05-27T10:00:08+08:00"}
            """.formatted(originalFileId, parsedLogId, datasetId, userId));

        String status = jdbcTemplate.queryForObject(
            "SELECT task_status FROM document_parsed_log WHERE id = ?", String.class, parsedLogId);
        String objectKey = jdbcTemplate.queryForObject(
            "SELECT parsed_object_key FROM document_parsed_log WHERE id = ?", String.class, parsedLogId);
        assertThat(status).isEqualTo("success");
        assertThat(objectKey).isEqualTo("parsed/guide.md");
    }
}

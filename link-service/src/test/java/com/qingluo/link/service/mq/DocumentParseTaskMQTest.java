package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

class DocumentParseTaskMQTest {

    @Test
    void Should_SerializePythonFlatTaskContract() {
        DocumentParseTaskMQ mq = new DocumentParseTaskMQ(firstParse());

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(mq.getMQName()).isEqualTo("tolink.rag.parse_task");
        assertThat(json).doesNotContainKeys("payload", "mq_name", "mq_type");
        assertThat(json.getLong("document_parse_file_id")).isEqualTo(301L);
        assertThat(json.getString("trigger_mode")).isEqualTo("manual_retry");
        assertThat(json.getString("pdf_parser_backend")).isEqualTo("opendataloader");
    }

    @Test
    void Should_RejectTaskWithoutValidTriggerMode() {
        DocumentParseTaskMQ.MsgPayload payload = firstParse();
        payload.setTriggerMode(null);

        assertThatThrownBy(() -> new DocumentParseTaskMQ(payload).getMessage())
            .hasMessage("parse_task trigger_mode is invalid");
    }

    @Test
    void Should_SerializeFirstParse_With_IsRetryFalseAndNoPreviousTaskId() {
        JSONObject json = JSON.parseObject(new DocumentParseTaskMQ(firstParse()).getMessage());

        assertThat(json.getBooleanValue("is_retry")).isFalse();
        assertThat(json.getString("previous_task_id")).isNull();
        assertThat(json.getString("md_object_key")).isEqualTo("parsed/first.md");
    }

    @Test
    void Should_SerializeRetry_With_IsRetryTrueAndReusedMarkdown() {
        DocumentParseTaskMQ.MsgPayload payload = firstParse();
        payload.setIsRetry(true);
        payload.setPreviousTaskId("task-old");
        payload.setMdBucket("rag-md");
        payload.setMdObjectKey("parsed/old.md");

        JSONObject json = JSON.parseObject(new DocumentParseTaskMQ(payload).getMessage());

        assertThat(json.getBooleanValue("is_retry")).isTrue();
        assertThat(json.getString("previous_task_id")).isEqualTo("task-old");
        assertThat(json.getString("md_bucket")).isEqualTo("rag-md");
        assertThat(json.getString("md_object_key")).isEqualTo("parsed/old.md");
    }

    @Test
    void Should_Reject_When_RetryMissingPreviousTaskId() {
        DocumentParseTaskMQ.MsgPayload payload = firstParse();
        payload.setIsRetry(true);
        payload.setPreviousTaskId(null);

        assertThatThrownBy(() -> new DocumentParseTaskMQ(payload).getMessage())
            .hasMessage("parse_task previous_task_id is missing for retry");
    }

    @Test
    void Should_Reject_When_RetryMissingMdBucket() {
        DocumentParseTaskMQ.MsgPayload payload = firstParse();
        payload.setIsRetry(true);
        payload.setPreviousTaskId("task-old");
        payload.setMdBucket(null);

        assertThatThrownBy(() -> new DocumentParseTaskMQ(payload).getMessage())
            .hasMessage("parse_task md object is missing");
    }

    @Test
    void Should_Reject_When_RetryMissingMdObjectKey() {
        DocumentParseTaskMQ.MsgPayload payload = firstParse();
        payload.setIsRetry(true);
        payload.setPreviousTaskId("task-old");
        payload.setMdObjectKey(null);

        assertThatThrownBy(() -> new DocumentParseTaskMQ(payload).getMessage())
            .hasMessage("parse_task md object is missing");
    }

    private DocumentParseTaskMQ.MsgPayload firstParse() {
        return new DocumentParseTaskMQ.MsgPayload(
            "task-1", 101L, 301L, 401L, 201L, "manual_retry", "pdf",
            "rag-raw", "raw/first.pdf", "first.pdf", "rag-md", "parsed/first.md", "opendataloader",
            false, null);
    }
}

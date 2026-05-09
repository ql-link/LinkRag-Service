package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeParseResultMQTest {

    @Test
    @DisplayName("Should_SerializeFlatJsonMessage_When_BuildParseResultMQ")
    void Should_SerializeFlatJsonMessage_When_BuildParseResultMQ() {
        KnowledgeParseResultMQ mq = new KnowledgeParseResultMQ(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            10001L,
            20001L,
            10002L,
            10003L,
            "success",
            null,
            "2026-04-28T10:00:08+08:00"
        ));

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(mq.getMQName()).isEqualTo("tolink.rag.parse_result");
        assertThat(json).doesNotContainKeys("payload", "mq_name", "mq_type");
        assertThat(json.getString("task_id")).isEqualTo("task-1");
        assertThat(json.getLong("original_file_id")).isEqualTo(10001L);
        assertThat(json.getLong("document_parse_log_id")).isEqualTo(20001L);
        assertThat(json.getLong("dataset_id")).isEqualTo(10002L);
        assertThat(json.getLong("user_id")).isEqualTo(10003L);
        assertThat(json.getString("task_status")).isEqualTo("success");
        assertThat(json.getString("parse_finished_at")).isEqualTo("2026-04-28T10:00:08+08:00");
    }

    @Test
    @DisplayName("Should_RejectMessage_When_ParseLogIdMissing")
    void Should_RejectMessage_When_ParseLogIdMissing() {
        KnowledgeParseResultMQ mq = new KnowledgeParseResultMQ(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            10001L,
            null,
            10002L,
            10003L,
            "success",
            null,
            "2026-04-28T10:00:08+08:00"
        ));

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("parse_result document_parse_log_id is missing");
    }

    @Test
    @DisplayName("Should_RejectMessage_When_TaskStatusInvalid")
    void Should_RejectMessage_When_TaskStatusInvalid() {
        KnowledgeParseResultMQ mq = new KnowledgeParseResultMQ(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            10001L,
            20001L,
            10002L,
            10003L,
            "processing",
            null,
            "2026-04-28T10:00:08+08:00"
        ));

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("parse_result task_status is invalid");
    }

    @Test
    @DisplayName("Should_RejectMessage_When_FailureReasonMissingForFailedStatus")
    void Should_RejectMessage_When_FailureReasonMissingForFailedStatus() {
        KnowledgeParseResultMQ mq = new KnowledgeParseResultMQ(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            10001L,
            20001L,
            10002L,
            10003L,
            "failed",
            null,
            "2026-04-28T10:00:08+08:00"
        ));

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("parse_result failure_reason is missing when task_status is failed");
    }
}

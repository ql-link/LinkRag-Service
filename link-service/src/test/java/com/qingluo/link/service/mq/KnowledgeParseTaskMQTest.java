package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeParseTaskMQTest {

    @Test
    @DisplayName("Should_SerializeFlatJsonMessage_When_BuildParseTaskMQ")
    void Should_SerializeFlatJsonMessage_When_BuildParseTaskMQ() {
        KnowledgeParseTaskMQ mq = new KnowledgeParseTaskMQ(new KnowledgeParseTaskMQ.MsgPayload(
            "task-1",
            10001L,
            20001L,  // parsed_file_id
            10002L,
            10003L,
            "pdf",
            "rag-raw",
            "original/user-10002/dataset-10003/2026/04/26/10001/report.pdf",
            "report.pdf",
            "rag-md",
            "parsed/user-10002/dataset-10003/2026/04/26/task-1/report.md"
        ));

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(mq.getMQName()).isEqualTo("tolink.rag.parse_task");
        assertThat(json).doesNotContainKeys("payload", "mq_name", "mq_type");
        assertThat(json.getString("task_id")).isEqualTo("task-1");
        assertThat(json.getLong("original_file_id")).isEqualTo(10001L);
        assertThat(json.getLong("parsed_file_id")).isEqualTo(20001L);
        assertThat(json.getLong("user_id")).isEqualTo(10002L);
        assertThat(json.getLong("dataset_id")).isEqualTo(10003L);
        assertThat(json.getString("source_bucket")).isEqualTo("rag-raw");
        assertThat(json.getString("md_bucket")).isEqualTo("rag-md");
    }

    @Test
    @DisplayName("Should_RejectMessage_When_TaskIdMissing")
    void Should_RejectMessage_When_TaskIdMissing() {
        KnowledgeParseTaskMQ mq = new KnowledgeParseTaskMQ(new KnowledgeParseTaskMQ.MsgPayload(
            "",
            10001L,
            20001L,  // parsed_file_id
            10002L,
            10003L,
            "pdf",
            "rag-raw",
            "source/key",
            "report.pdf",
            "rag-md",
            "md/key"
        ));

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("parse_task task_id is missing");
    }

    @Test
    @DisplayName("Should_RejectMessage_When_ParsedFileIdMissing")
    void Should_RejectMessage_When_ParsedFileIdMissing() {
        KnowledgeParseTaskMQ mq = new KnowledgeParseTaskMQ(new KnowledgeParseTaskMQ.MsgPayload(
            "task-1",
            10001L,
            null,
            10002L,
            10003L,
            "pdf",
            "rag-raw",
            "source/key",
            "report.pdf",
            "rag-md",
            "md/key"
        ));

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("parse_task parsed_file_id is missing");
    }
}

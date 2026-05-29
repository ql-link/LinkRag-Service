package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

class DocumentParseTaskMQTest {

    @Test
    void Should_SerializePythonFlatTaskContract() {
        DocumentParseTaskMQ mq = new DocumentParseTaskMQ(new DocumentParseTaskMQ.MsgPayload(
            "task-1", 10L, 20L, 30L, 40L, "manual_retry", "pdf",
            "rag-raw", "raw/a.pdf", "a.pdf", "rag-md", "parsed/a.md"));

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(mq.getMQName()).isEqualTo("tolink.rag.parse_task");
        assertThat(json).doesNotContainKeys("payload", "mq_name", "mq_type");
        assertThat(json.getLong("document_parse_file_id")).isEqualTo(20L);
        assertThat(json.getString("trigger_mode")).isEqualTo("manual_retry");
    }

    @Test
    void Should_RejectTaskWithoutValidTriggerMode() {
        DocumentParseTaskMQ mq = new DocumentParseTaskMQ(new DocumentParseTaskMQ.MsgPayload(
            "task-1", 10L, 20L, 30L, 40L, null, "pdf",
            "rag-raw", "raw/a.pdf", "a.pdf", "rag-md", "parsed/a.md"));

        assertThatThrownBy(mq::getMessage).hasMessage("parse_task trigger_mode is invalid");
    }
}

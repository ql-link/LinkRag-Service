package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import org.junit.jupiter.api.Test;

class KnowledgeParseResultMQTest {

    @Test
    void Should_SerializePythonFlatResultContract() {
        KnowledgeParseResultMQ mq = new KnowledgeParseResultMQ(new KnowledgeParseResultMQ.MsgPayload(
            "task-1", 10L, 20L, 30L, 40L, "success", null, "2026-05-27T10:00:08+08:00"));

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(mq.getMQName()).isEqualTo("tolink.rag.parse_result");
        assertThat(json).doesNotContainKeys("payload", "mq_name", "mq_type");
        assertThat(json.getLong("document_parsed_log_id")).isEqualTo(20L);
    }

    @Test
    void Should_RejectFailedResultWithoutReason() {
        KnowledgeParseResultMQ mq = new KnowledgeParseResultMQ(new KnowledgeParseResultMQ.MsgPayload(
            "task-1", 10L, 20L, 30L, 40L, "failed", null, "2026-05-27T10:00:08+08:00"));

        assertThatThrownBy(mq::getMessage)
            .hasMessage("parse_result failure_reason is missing when task_status is failed");
    }
}

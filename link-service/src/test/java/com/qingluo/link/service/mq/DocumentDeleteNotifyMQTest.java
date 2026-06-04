package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.components.mq.constant.MQSendType;
import org.junit.jupiter.api.Test;

class DocumentDeleteNotifyMQTest {

    @Test
    void Should_SerializeFileScopeFlatContract() {
        DocumentDeleteNotifyMQ mq = DocumentDeleteNotifyMQ.forFile(1L, 200L, 100L);

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(mq.getMQName()).isEqualTo("tolink.rag.document_delete");
        assertThat(mq.getMQType()).isEqualTo(MQSendType.QUEUE);
        // 扁平 snake_case，不含包装键
        assertThat(json).doesNotContainKeys("payload", "mq_name", "mq_type");
        assertThat(json.getString("delete_type")).isEqualTo("file");
        assertThat(json.getLong("original_file_id")).isEqualTo(1L);
        assertThat(json.getLong("dataset_id")).isEqualTo(200L);
        assertThat(json.getLong("user_id")).isEqualTo(100L);
    }

    @Test
    void Should_SerializeDatasetScope_WithoutOriginalFileId() {
        DocumentDeleteNotifyMQ mq = DocumentDeleteNotifyMQ.forDataset(10L, 100L);

        JSONObject json = JSON.parseObject(mq.getMessage());

        assertThat(json.getString("delete_type")).isEqualTo("dataset");
        assertThat(json.getLong("dataset_id")).isEqualTo(10L);
        assertThat(json.getLong("user_id")).isEqualTo(100L);
        // dataset 范围不下发文件 id：original_file_id 为 null，fastjson 默认省略该键
        assertThat(json.containsKey("original_file_id")).isFalse();
    }

    @Test
    void Should_RejectInvalidDeleteType() {
        DocumentDeleteNotifyMQ.MsgPayload payload = new DocumentDeleteNotifyMQ.MsgPayload();
        payload.setDeleteType("unknown");
        payload.setDatasetId(10L);
        payload.setUserId(100L);

        assertThatThrownBy(() -> new DocumentDeleteNotifyMQ(payload).getMessage())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("document_delete delete_type is invalid");
    }

    @Test
    void Should_RejectMissingDatasetId() {
        DocumentDeleteNotifyMQ mq = DocumentDeleteNotifyMQ.forFile(1L, null, 100L);

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("document_delete ownership is missing");
    }

    @Test
    void Should_RejectMissingUserId() {
        DocumentDeleteNotifyMQ mq = DocumentDeleteNotifyMQ.forDataset(10L, null);

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("document_delete ownership is missing");
    }

    @Test
    void Should_RejectFileScope_WithoutOriginalFileId() {
        DocumentDeleteNotifyMQ mq = DocumentDeleteNotifyMQ.forFile(null, 200L, 100L);

        assertThatThrownBy(mq::getMessage)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("document_delete original_file_id is missing for file");
    }
}

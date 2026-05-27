package com.qingluo.link.components.mq.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQSendType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * Python 向 Java 回传的解析终态消息。
 */
public class KnowledgeParseResultMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.rag.parse_result";

    private MsgPayload msgPayload;

    public KnowledgeParseResultMQ() {
        this.msgPayload = new MsgPayload();
    }

    public KnowledgeParseResultMQ(MsgPayload msgPayload) {
        this.msgPayload = msgPayload;
    }

    public static MsgPayload parseMsg(String msg) {
        MsgPayload payload = JSON.parseObject(msg).toJavaObject(MsgPayload.class);
        validate(payload);
        return payload;
    }

    @Override
    public String getMQName() {
        return MQ_NAME;
    }

    @Override
    public MQSendType getMQType() {
        return MQSendType.QUEUE;
    }

    @Override
    public String getMessage() {
        validate(msgPayload);
        return JSON.toJSONString(msgPayload);
    }

    public interface MQReceiver {
        void receive(MsgPayload msgPayload);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "task_id")
        private String taskId;
        @JSONField(name = "original_file_id")
        private Long originalFileId;
        @JSONField(name = "document_parsed_log_id")
        private Long documentParsedLogId;
        @JSONField(name = "dataset_id")
        private Long datasetId;
        @JSONField(name = "user_id")
        private Long userId;
        @JSONField(name = "task_status")
        private String taskStatus;
        @JSONField(name = "failure_reason")
        private String failureReason;
        @JSONField(name = "parse_finished_at")
        private String parseFinishedAt;
    }

    private static void validate(MsgPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("parse_result payload is missing");
        }
        if (!StringUtils.hasText(payload.getTaskId())) {
            throw new IllegalArgumentException("parse_result task_id is missing");
        }
        if (payload.getOriginalFileId() == null) {
            throw new IllegalArgumentException("parse_result original_file_id is missing");
        }
        if (payload.getDocumentParsedLogId() == null) {
            throw new IllegalArgumentException("parse_result document_parsed_log_id is missing");
        }
        if (payload.getDatasetId() == null || payload.getUserId() == null) {
            throw new IllegalArgumentException("parse_result ownership is missing");
        }
        if (!"success".equals(payload.getTaskStatus()) && !"failed".equals(payload.getTaskStatus())) {
            throw new IllegalArgumentException("parse_result task_status is invalid");
        }
        if ("success".equals(payload.getTaskStatus()) && payload.getFailureReason() != null) {
            throw new IllegalArgumentException("parse_result failure_reason must be null when task_status is success");
        }
        if ("failed".equals(payload.getTaskStatus()) && !StringUtils.hasText(payload.getFailureReason())) {
            throw new IllegalArgumentException("parse_result failure_reason is missing when task_status is failed");
        }
        if (!StringUtils.hasText(payload.getParseFinishedAt())) {
            throw new IllegalArgumentException("parse_result parse_finished_at is missing");
        }
    }
}

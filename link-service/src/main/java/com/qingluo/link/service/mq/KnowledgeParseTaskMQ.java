package com.qingluo.link.service.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQSendType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * Java 向 Python 投递的原文件解析任务消息。
 */
public class KnowledgeParseTaskMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.rag.parse_task";

    private MsgPayload msgPayload;

    public KnowledgeParseTaskMQ() {
        this.msgPayload = new MsgPayload();
    }

    public KnowledgeParseTaskMQ(MsgPayload msgPayload) {
        this.msgPayload = msgPayload;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "task_id")
        private String taskId;
        @JSONField(name = "original_file_id")
        private Long originalFileId;
        @JSONField(name = "document_parse_file_id")
        private Long documentParseFileId;
        @JSONField(name = "user_id")
        private Long userId;
        @JSONField(name = "dataset_id")
        private Long datasetId;
        @JSONField(name = "trigger_mode")
        private String triggerMode;
        @JSONField(name = "file_type")
        private String fileType;
        @JSONField(name = "source_bucket")
        private String sourceBucket;
        @JSONField(name = "source_object_key")
        private String sourceObjectKey;
        @JSONField(name = "source_filename")
        private String sourceFilename;
        @JSONField(name = "md_bucket")
        private String mdBucket;
        @JSONField(name = "md_object_key")
        private String mdObjectKey;
    }

    private static void validate(MsgPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.getTaskId())) {
            throw new IllegalArgumentException("parse_task task_id is missing");
        }
        if (payload.getOriginalFileId() == null || payload.getDocumentParseFileId() == null) {
            throw new IllegalArgumentException("parse_task file identity is missing");
        }
        if (payload.getUserId() == null || payload.getDatasetId() == null) {
            throw new IllegalArgumentException("parse_task ownership is missing");
        }
        if (!"upload_auto".equals(payload.getTriggerMode()) && !"manual_retry".equals(payload.getTriggerMode())) {
            throw new IllegalArgumentException("parse_task trigger_mode is invalid");
        }
        if (!StringUtils.hasText(payload.getSourceBucket()) || !StringUtils.hasText(payload.getSourceObjectKey())) {
            throw new IllegalArgumentException("parse_task source object is missing");
        }
        if (!StringUtils.hasText(payload.getMdBucket()) || !StringUtils.hasText(payload.getMdObjectKey())) {
            throw new IllegalArgumentException("parse_task md object is missing");
        }
    }
}

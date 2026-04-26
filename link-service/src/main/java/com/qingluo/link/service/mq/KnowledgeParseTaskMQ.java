package com.qingluo.link.service.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSendType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 原文件解析任务 MQ 消息。
 *
 * <p>二期与 Python 约定使用扁平 JSON 消息体，不再套 envelope。
 * task_id 是解析链路的业务幂等键，必须与 document_parse_task.task_id 保持一致。
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

    private static void validate(MsgPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("parse_task payload is missing");
        }
        if (!StringUtils.hasText(payload.getTaskId())) {
            throw new IllegalArgumentException("parse_task task_id is missing");
        }
        if (payload.getOriginalFileId() == null) {
            throw new IllegalArgumentException("parse_task original_file_id is missing");
        }
        if (payload.getUserId() == null) {
            throw new IllegalArgumentException("parse_task user_id is missing");
        }
        if (payload.getDatasetId() == null) {
            throw new IllegalArgumentException("parse_task dataset_id is missing");
        }
        if (!StringUtils.hasText(payload.getSourceBucket()) || !StringUtils.hasText(payload.getSourceObjectKey())) {
            throw new IllegalArgumentException("parse_task source object is missing");
        }
        if (!StringUtils.hasText(payload.getMdBucket()) || !StringUtils.hasText(payload.getMdObjectKey())) {
            throw new IllegalArgumentException("parse_task md object is missing");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "task_id")
        private String taskId;
        @JSONField(name = "original_file_id")
        private Long originalFileId;
        @JSONField(name = "user_id")
        private Long userId;
        @JSONField(name = "dataset_id")
        private Long datasetId;
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
}

package com.qingluo.link.service.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSendType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

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
        JSONObject envelope = JSON.parseObject(msg);
        JSONObject payload = envelope.getJSONObject("payload");
        if (payload == null) {
            throw new IllegalArgumentException("parse_result payload is missing");
        }
        MsgPayload parsed = payload.toJavaObject(MsgPayload.class);
        if (!StringUtils.hasText(parsed.getTaskId())) {
            throw new IllegalArgumentException("parse_result task_id is missing");
        }
        if (!StringUtils.hasText(parsed.getDocumentId())) {
            throw new IllegalArgumentException("parse_result document_id is missing");
        }
        return parsed;
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
        return JSON.toJSONString(new Envelope("parse_result", MQ_NAME, msgPayload));
    }

    public interface MQReceiver {
        void receive(MsgPayload msgPayload);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        private String taskId;
        private String documentId;
        private Boolean success;
        private String status;
        private String parsedBucketName;
        private String parsedObjectKey;
        private String parsedFileUrl;
        private String failureReason;
        private Long timeCostMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Envelope {
        private String mqType;
        private String mqName;
        private MsgPayload payload;
    }
}

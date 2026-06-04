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
 * Java 向 Python RAG 投递的 LLM 配置缓存同步消息。
 */
public class RagCacheSyncMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.rag.cache_sync";
    public static final String ACTION_REFRESH = "refresh";
    public static final String ACTION_INVALIDATE = "invalidate";

    private MsgPayload msgPayload;

    public RagCacheSyncMQ() {
        this.msgPayload = new MsgPayload();
    }

    public RagCacheSyncMQ(MsgPayload msgPayload) {
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
        @JSONField(name = "user_id")
        private String userId;
        @JSONField(name = "config_id")
        private String configId;
        @JSONField(name = "action")
        private String action;
    }

    private static void validate(MsgPayload payload) {
        if (payload == null || !StringUtils.hasText(payload.getUserId())) {
            throw new IllegalArgumentException("cache_sync user_id is missing");
        }
        if (!ACTION_REFRESH.equals(payload.getAction()) && !ACTION_INVALIDATE.equals(payload.getAction())) {
            throw new IllegalArgumentException("cache_sync action is invalid");
        }
        if (ACTION_INVALIDATE.equals(payload.getAction()) && !StringUtils.hasText(payload.getConfigId())) {
            throw new IllegalArgumentException("cache_sync config_id is missing for invalidate");
        }
    }
}

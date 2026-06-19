package com.qingluo.link.components.mq.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQSendType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Python 向 Java 回传的对话轮次完成消息（一轮问答的完整数据，供 Java 落库）。
 *
 * <p>Topic：{@code tolink.rag.chat_turn}，routing_key = conversation_id（同一对话有序）。
 * Python 在问答正常结束 / 生成失败 / 客户端断连时各发一条，空召回不发。</p>
 *
 * <p>线格式为统一信封 {@code {"mq_type","mq_name","payload":{...}}}，业务字段在 payload 内，
 * 故 {@link #parseMsg(String)} 需先解包 payload 再反序列化（兼容无信封的扁平结构）。</p>
 */
public class ChatTurnMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.rag.chat_turn";

    private MsgPayload msgPayload;

    public ChatTurnMQ() {
        this.msgPayload = new MsgPayload();
    }

    public ChatTurnMQ(MsgPayload msgPayload) {
        this.msgPayload = msgPayload;
    }

    /**
     * 解包信封并反序列化业务载荷，同时做最小必填校验。
     */
    public static MsgPayload parseMsg(String msg) {
        JSONObject root = JSON.parseObject(msg);
        JSONObject payloadJson = root.getJSONObject("payload");
        // 兼容：若上游未包信封（payload 缺失），按扁平结构解析。
        JSONObject target = payloadJson != null ? payloadJson : root;
        MsgPayload payload = target.toJavaObject(MsgPayload.class);
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

    /**
     * 业务侧对话轮次消息接收契约。
     */
    public interface MQReceiver {
        void receive(MsgPayload msgPayload);
    }

    /**
     * 对话轮次完成载荷（字段与 LinkRag ChatTurnPayload 对齐，JSON 为 snake_case）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "message_id")
        private String messageId;
        @JSONField(name = "timestamp")
        private Double timestamp;
        @JSONField(name = "conversation_id")
        private Long conversationId;
        @JSONField(name = "request_id")
        private String requestId;
        @JSONField(name = "user_id")
        private Long userId;
        @JSONField(name = "query")
        private String query;
        @JSONField(name = "answer")
        private String answer;
        @JSONField(name = "config_id")
        private Long configId;
        @JSONField(name = "provider_type")
        private String providerType;
        @JSONField(name = "model_name")
        private String modelName;
        @JSONField(name = "prompt_tokens")
        private Integer promptTokens;
        @JSONField(name = "completion_tokens")
        private Integer completionTokens;
        @JSONField(name = "total_tokens")
        private Integer totalTokens;
        @JSONField(name = "references")
        private List<String> references;
        @JSONField(name = "latency_ms")
        private Integer latencyMs;
        @JSONField(name = "status")
        private String status;
    }

    /**
     * 校验对话轮次消息的最小必填字段与状态枚举。
     */
    private static void validate(MsgPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("chat_turn payload is missing");
        }
        if (payload.getConversationId() == null) {
            throw new IllegalArgumentException("chat_turn conversation_id is missing");
        }
        if (payload.getUserId() == null) {
            throw new IllegalArgumentException("chat_turn user_id is missing");
        }
        if (!StringUtils.hasText(payload.getRequestId())) {
            throw new IllegalArgumentException("chat_turn request_id is missing");
        }
        String status = payload.getStatus();
        if (!"success".equals(status) && !"partial".equals(status) && !"failed".equals(status)) {
            throw new IllegalArgumentException("chat_turn status is invalid: " + status);
        }
    }
}

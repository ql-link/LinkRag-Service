package com.qingluo.link.components.mq.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQSendType;
import com.qingluo.link.components.mq.constant.UsageOperation;
import com.qingluo.link.components.mq.constant.UsageStage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * Python 向 Java 回传的统一 Token 用量上报消息，承载<b>全部模型调用</b>用量：
 * 解析侧 embed/vision/table、召回侧 embed/rerank，以及对话最终 generate（{@code stage=chat}/{@code operation=generate}）。
 *
 * <p>Topic：{@code tolink.rag.usage_report}，routing_key = user_id（按用户分区）。
 * 一条用量记录回答「某用户、某阶段、某操作、用了哪个模型、消耗多少 token」，落 {@code llm_usage_log} 一行。
 * 对话内容（query/answer/references 等）仍走另一通道 {@link ChatTurnMQ}（topic {@code tolink.rag.chat_turn}），
 * 但其 token 自 LINK-191 起改由本通道承接，{@code chat_turn} 不再触发 {@code llm_usage_log} 落库。</p>
 *
 * <p>线格式为统一信封 {@code {"mq_type","mq_name","payload":{...}}}，业务字段在 payload 内、全 snake_case；
 * {@link #parseMsg(String)} 先解包 payload 再反序列化（兼容无信封的扁平结构）。
 * generate 行不带 {@code conversation_id}/{@code request_id}（瘦身后表已无这些列），无法回溯到具体对话。</p>
 *
 * <p>可靠性：旁路、最终一致。Python 上报失败只告警不阻断主链路，偶发丢条可接受；全缓存命中（token=0）不上报。</p>
 */
public class UsageReportMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.rag.usage_report";

    private MsgPayload msgPayload;

    public UsageReportMQ() {
        this.msgPayload = new MsgPayload();
    }

    public UsageReportMQ(MsgPayload msgPayload) {
        this.msgPayload = msgPayload;
    }

    /**
     * 解包信封并反序列化业务载荷，同时做最小必填与枚举校验。
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
     * 业务侧用量上报消息接收契约。
     */
    public interface MQReceiver {
        void receive(MsgPayload msgPayload);
    }

    /**
     * 用量上报载荷（字段与 LinkRag UsageReportPayload 对齐，JSON 为 snake_case）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "message_id")
        private String messageId;
        @JSONField(name = "timestamp")
        private Double timestamp;
        @JSONField(name = "user_id")
        private Long userId;
        @JSONField(name = "provider_type")
        private String providerType;
        @JSONField(name = "model_name")
        private String modelName;
        @JSONField(name = "stage")
        private String stage;
        @JSONField(name = "operation")
        private String operation;
        @JSONField(name = "prompt_tokens")
        private Integer promptTokens;
        @JSONField(name = "completion_tokens")
        private Integer completionTokens;
        @JSONField(name = "total_tokens")
        private Integer totalTokens;
        @JSONField(name = "config_id")
        private Long configId;
        /** 解析任务锚点（parse·embed 携带）。当前 llm_usage_log 无独立 task 列，仅作审计锚点、不落库。 */
        @JSONField(name = "task_id")
        private String taskId;
        @JSONField(name = "latency_ms")
        private Integer latencyMs;
        @JSONField(name = "status")
        private String status;
    }

    /**
     * 校验用量上报消息的必填字段与枚举：user_id / provider_type / model_name / stage / operation /
     * 三个 token 列必填；stage / operation 取值受限；status 缺省视为 success，给定时必须合法。
     */
    private static void validate(MsgPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("usage_report payload is missing");
        }
        if (payload.getUserId() == null) {
            throw new IllegalArgumentException("usage_report user_id is missing");
        }
        if (!StringUtils.hasText(payload.getProviderType())) {
            throw new IllegalArgumentException("usage_report provider_type is missing");
        }
        if (!StringUtils.hasText(payload.getModelName())) {
            throw new IllegalArgumentException("usage_report model_name is missing");
        }
        if (!UsageStage.isValid(payload.getStage())) {
            throw new IllegalArgumentException("usage_report stage is invalid: " + payload.getStage());
        }
        if (!UsageOperation.isValid(payload.getOperation())) {
            throw new IllegalArgumentException("usage_report operation is invalid: " + payload.getOperation());
        }
        if (payload.getPromptTokens() == null
                || payload.getCompletionTokens() == null
                || payload.getTotalTokens() == null) {
            throw new IllegalArgumentException("usage_report token fields are missing");
        }
        String status = payload.getStatus();
        if (StringUtils.hasText(status)
                && !"success".equals(status) && !"partial".equals(status) && !"failed".equals(status)) {
            throw new IllegalArgumentException("usage_report status is invalid: " + status);
        }
    }
}

package com.qingluo.link.service.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSendType;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 缓存补偿 MQ 消息模型。
 *
 * <p>Canal 或其他 CDC 桥把数据库变更事实转换成这条扁平消息，
 * Java 消费端收到后只负责二次删除缓存，不做异步重建。</p>
 */
public class CacheCompensationMQ implements AbstractMQ {

    public static final String MQ_NAME = "tolink.cache.evict";

    private MsgPayload msgPayload;

    public CacheCompensationMQ() {
        this.msgPayload = new MsgPayload();
    }

    public CacheCompensationMQ(MsgPayload msgPayload) {
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

    /**
     * 业务侧补偿消息接收契约。
     */
    public interface MQReceiver {
        void receive(MsgPayload payload);
    }

    /**
     * 缓存补偿消息载体。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MsgPayload {
        @JSONField(name = "event_id")
        private String eventId;
        @JSONField(name = "cache_target")
        private String cacheTarget;
        @JSONField(name = "route_id")
        private String routeId;
        @JSONField(name = "source_table")
        private String sourceTable;
        @JSONField(name = "operation_type")
        private String operationType;
        @JSONField(name = "trace_id")
        private String traceId;
        @JSONField(name = "occurred_at")
        private String occurredAt;

        /**
         * 把消息中的逻辑目标解析成统一枚举，避免消费者自己判断字符串。
         */
        public CacheEvictTarget parseTarget() {
            return CacheEvictTarget.fromCode(cacheTarget);
        }
    }

    /**
     * 校验缓存补偿消息的最小必填字段。
     */
    private static void validate(MsgPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("cache_evict payload is missing");
        }
        if (!StringUtils.hasText(payload.getEventId())) {
            throw new IllegalArgumentException("cache_evict event_id is missing");
        }
        if (!StringUtils.hasText(payload.getCacheTarget())) {
            throw new IllegalArgumentException("cache_evict cache_target is missing");
        }
        CacheEvictTarget.fromCode(payload.getCacheTarget());
        if (!StringUtils.hasText(payload.getRouteId())) {
            throw new IllegalArgumentException("cache_evict route_id is missing");
        }
        if (!StringUtils.hasText(payload.getOperationType())) {
            throw new IllegalArgumentException("cache_evict operation_type is missing");
        }
        if (!StringUtils.hasText(payload.getOccurredAt())) {
            throw new IllegalArgumentException("cache_evict occurred_at is missing");
        }
    }
}

package com.qingluo.link.service.mq.config;

import com.qingluo.link.service.support.CdcBridgeMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * CDC 桥接（tolink.canal.binlog）专用的 Kafka 监听容器工厂与错误处理器。
 *
 * <p>对齐 {@link ParseResultKafkaConfig} 的失败分类，避免默认工厂零退避导致坏消息毫秒级空转：</p>
 * <ul>
 *   <li>不可重试（坏 JSON / 缺字段 = {@link IllegalArgumentException}、Kafka 反序列化 = {@link DeserializationException}）→ 立即 recover，不重试；</li>
 *   <li>可重试（发送失败 / 基础设施异常）→ 指数退避重试，耗尽后 recover。</li>
 * </ul>
 * <p>recover 动作为告警日志 + 监控指标 + 提交跳过，不引入 DLQ。</p>
 */
@Configuration
@ConditionalOnExpression(CdcBridgeKafkaConfig.CDC_BRIDGE_CONDITION)
@Slf4j
public class CdcBridgeKafkaConfig {

    /**
     * CDC 桥接装配条件：vender=kafka（Kafka 底座存在）且 cdc.enabled=true（总开关打开）。
     *
     * <p>消费者 {@link com.qingluo.link.service.mq.cdc.CdcBridgeKafkaReceiver} 与本配置共用此条件，
     * 确保 CDC 开关一处生效、口径一致，杜绝“消费者不装但容器工厂仍被创建”的半开状态。
     * 两条件须用单个 SpEL 合并：同类型 {@code @ConditionalOnProperty} 无法在同一元素上叠加。</p>
     */
    public static final String CDC_BRIDGE_CONDITION =
            "'${tolink.mq.vender:}'.equals('kafka') "
                    + "and '${tolink.cache-consistency.cdc.enabled:false}'.equals('true')";

    /** 退避重试最大次数（不含首次投递）。 */
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 10_000L;

    /**
     * CDC 桥接专用监听容器工厂，复用 Boot 自动装配的 ConsumerFactory，仅覆盖错误处理。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> cdcBridgeKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory, CdcBridgeMetrics metrics) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setErrorHandler(cdcBridgeErrorHandler(metrics));
        return factory;
    }

    /**
     * 自定义错误处理器：带退避重试 + 不可重试分类 + 告警/指标 recoverer。
     */
    SeekToCurrentErrorHandler cdcBridgeErrorHandler(CdcBridgeMetrics metrics) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);

        SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler(
                (record, exception) -> recover(record, exception, metrics), backOff);
        // 坏消息无重试价值，立即 recover：
        // - IllegalArgumentException：Canal 解析/必填校验失败（坏 JSON、缺 table、provider_id 非数字等）；
        // - DeserializationException：Kafka 反序列化失败。
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                DeserializationException.class);
        return handler;
    }

    /**
     * 失败兜底：告警日志 + 监控指标 + 提交跳过；永不抛出，避免无限重投。
     */
    void recover(ConsumerRecord<?, ?> record, Exception exception, CdcBridgeMetrics metrics) {
        Throwable cause = rootCause(exception);
        String reason = classify(cause);
        metrics.recordRecover(reason);
        log.warn("Recover cdc_bridge message after error, reason={}, topic={}, partition={}, offset={}, "
                        + "payload={}, cause={}",
                reason, record.topic(), record.partition(), record.offset(), record.value(),
                cause == null ? null : cause.toString());
    }

    String classify(Throwable cause) {
        if (cause instanceof IllegalArgumentException || cause instanceof DeserializationException) {
            return "bad_payload";
        }
        return "infra_exhausted";
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}

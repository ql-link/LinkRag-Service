package com.qingluo.link.service.mq.config;

import com.qingluo.link.components.mq.constant.MQVenderChoose;
import com.qingluo.link.service.exception.NonRetryableParseResultException;
import com.qingluo.link.service.exception.ParseResultPendingException;
import com.qingluo.link.service.support.ParseResultMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * parse_result（tolink.rag.parse_result）专用的 Kafka 监听容器工厂与错误处理器。
 *
 * <p>背景见 docs/parse-result-consumer-resilience：默认容器工厂用零退避的
 * FixedBackOff(0,9)，业务坏消息会毫秒级空转 10 次后静默丢弃，瞬时故障消息也会
 * 被无退避耗尽而误丢。本配置仅作用于 parse_result（缓存补偿仍走默认工厂），
 * 将失败分类为：</p>
 * <ul>
 *   <li>不可恢复（{@link NonRetryableParseResultException} / 坏 JSON）→ 立即 recover，不重试；</li>
 *   <li>可重试（{@link ParseResultPendingException} / 基础设施异常）→ 带退避重试，耗尽后 recover。</li>
 * </ul>
 * <p>recover 动作为告警日志 + 监控指标 + 提交跳过，不引入 DLQ。</p>
 */
@Configuration
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.KAFKA)
@Slf4j
public class ParseResultKafkaConfig {

    /** 退避重试最大次数（不含首次投递）。 */
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 10_000L;

    /**
     * parse_result 专用监听容器工厂，复用 Boot 自动装配的 ConsumerFactory，仅覆盖错误处理。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> parseResultKafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory, ParseResultMetrics metrics) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setErrorHandler(parseResultErrorHandler(metrics));
        return factory;
    }

    /**
     * 自定义错误处理器：带退避重试 + 不可重试分类 + 告警/指标 recoverer。
     */
    SeekToCurrentErrorHandler parseResultErrorHandler(ParseResultMetrics metrics) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);

        SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler(
                (record, exception) -> recover(record, exception, metrics), backOff);
        // 业务不可恢复异常立即 recover，不消耗退避重试：
        // - NonRetryableParseResultException：消息与已持久化终态逻辑矛盾；
        // - IllegalArgumentException：parseMsg 反序列化校验失败（坏 JSON / 缺字段）；
        // - DeserializationException：Kafka 反序列化失败。
        handler.addNotRetryableExceptions(
                NonRetryableParseResultException.class,
                IllegalArgumentException.class,
                DeserializationException.class);
        // 不调用 setCommitRecovered(true)：该选项要求容器 AckMode.MANUAL_IMMEDIATE，
        // 而本工厂沿用默认 BATCH ackMode。BATCH 下 recover 后失败记录被移出待处理集合、
        // 位移随正常 BATCH 提交推进，坏消息照样跳过、不阻塞后续（已由集成测试验证）。
        return handler;
    }

    /**
     * 失败兜底：告警日志 + 监控指标 + 提交跳过；永不抛出，避免无限重投。
     */
    void recover(ConsumerRecord<?, ?> record, Exception exception, ParseResultMetrics metrics) {
        Throwable cause = rootCause(exception);
        String reason = classify(cause);
        metrics.recordRecover(reason);
        log.warn("Recover parse_result message after error, reason={}, topic={}, partition={}, offset={}, "
                + "payload={}, cause={}",
            reason, record.topic(), record.partition(), record.offset(), record.value(),
            cause == null ? null : cause.toString());
    }

    String classify(Throwable cause) {
        if (cause instanceof NonRetryableParseResultException) {
            return "non_retryable";
        }
        if (cause instanceof ParseResultPendingException) {
            return "pending_exhausted";
        }
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

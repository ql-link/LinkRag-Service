package com.qingluo.link.service.mq.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.service.support.CacheCompensationMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

@Configuration
@ConditionalOnProperty(name = "tolink.mq.vender", havingValue = "kafka")
@Slf4j
public class CacheCompensationKafkaConfig {

    static final int MAX_DELIVERIES = 3;
    static final int MAX_RETRIES = MAX_DELIVERIES - 1;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> cacheCompensationKafkaListenerContainerFactory(
        ConsumerFactory<Object, Object> consumerFactory,
        KafkaTemplate<String, String> kafkaTemplate,
        CacheCompensationMetrics metrics) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setErrorHandler(errorHandler(CacheDeadLetterSupport.create(kafkaTemplate), metrics));
        return factory;
    }

    SeekToCurrentErrorHandler errorHandler(
        ConsumerRecordRecoverer deadLetterRecoverer, CacheCompensationMetrics metrics) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000L);
        SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler(
            (record, exception) -> recover(record, exception, deadLetterRecoverer, metrics), backOff);
        handler.setRetryListeners(retryListener(metrics));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, DeserializationException.class);
        return handler;
    }

    RetryListener retryListener(CacheCompensationMetrics metrics) {
        return (record, exception, deliveryAttempt) ->
            metrics.recordConsumeFailure(cacheTarget(record), classify(exception));
    }

    void recover(ConsumerRecord<?, ?> record, Exception exception,
        ConsumerRecordRecoverer deadLetterRecoverer, CacheCompensationMetrics metrics) {
        String reason = classify(exception);
        if ("BAD_PAYLOAD".equals(reason) || "UNKNOWN_TARGET".equals(reason)) {
            // Spring Kafka skips RetryListener.failedDelivery for non-retryable exceptions.
            metrics.recordConsumeFailure(cacheTarget(record), reason);
        }
        deadLetterRecoverer.accept(record, exception);
        String target = cacheTarget(record);
        metrics.recordDlt(target, reason);
        log.error("Cache compensation delivery exhausted and published to DLT, target={}, reason={}",
            target, reason);
    }

    String classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalArgumentException
                && current.getMessage() != null
                && current.getMessage().startsWith("Unknown cache target:")) {
                return "UNKNOWN_TARGET";
            }
            if (current instanceof IllegalArgumentException
                || current instanceof DeserializationException) {
                return "BAD_PAYLOAD";
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return "DELETE_EXHAUSTED";
    }

    String cacheTarget(ConsumerRecord<?, ?> record) {
        if (record == null || record.value() == null) {
            return "unknown";
        }
        try {
            JSONObject json = JSON.parseObject(String.valueOf(record.value()));
            String target = json == null ? null : json.getString("cache_target");
            return target == null || target.isBlank() ? "unknown" : target;
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }
}

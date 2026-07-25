package com.qingluo.link.service.mq.config;

import com.qingluo.link.service.mq.cdc.CdcEventException;
import com.qingluo.link.service.support.CdcBridgeMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

@Configuration
@ConditionalOnExpression(CdcBridgeKafkaConfig.CDC_BRIDGE_CONDITION)
@Slf4j
public class CdcBridgeKafkaConfig {

    public static final String CDC_BRIDGE_CONDITION =
        "'${tolink.mq.vender:}'.equals('kafka') "
            + "and '${tolink.cache-consistency.cdc.enabled:false}'.equals('true')";

    private static final int MAX_RETRIES = 3;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> cdcBridgeKafkaListenerContainerFactory(
        ConsumerFactory<Object, Object> consumerFactory,
        CdcBridgeMetrics metrics,
        KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setErrorHandler(cdcBridgeErrorHandler(
            metrics, CacheDeadLetterSupport.create(kafkaTemplate)));
        return factory;
    }

    SeekToCurrentErrorHandler cdcBridgeErrorHandler(
        CdcBridgeMetrics metrics, ConsumerRecordRecoverer deadLetterRecoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler(
            (record, exception) -> recover(record, exception, metrics, deadLetterRecoverer), backOff);
        handler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            DeserializationException.class);
        return handler;
    }

    void recover(ConsumerRecord<?, ?> record, Exception exception,
                 CdcBridgeMetrics metrics, ConsumerRecordRecoverer deadLetterRecoverer) {
        String reason = classify(exception);
        metrics.recordRecover(reason.toLowerCase());
        deadLetterRecoverer.accept(record, exception);
        log.warn("Published cdc_bridge failure to DLT, reason={}, topic={}, partition={}, offset={}",
            reason, record.topic(), record.partition(), record.offset());
    }

    String classify(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof CdcEventException eventException) {
                return eventException.getReason();
            }
            if (current instanceof DeserializationException || current instanceof IllegalArgumentException) {
                return "BAD_PAYLOAD";
            }
            current = current.getCause();
        }
        return "SEND_EXHAUSTED";
    }

}

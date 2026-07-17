package com.qingluo.link.service.mq.config;

import com.qingluo.link.service.support.CacheCompensationMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "tolink.mq.vender", havingValue = "kafka")
public class CacheCompensationKafkaConfig {

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
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(5_000L);
        SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler(
            (record, exception) -> recover(record, exception, deadLetterRecoverer, metrics), backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class, DeserializationException.class);
        return handler;
    }

    void recover(ConsumerRecord<?, ?> record, Exception exception,
                 ConsumerRecordRecoverer deadLetterRecoverer, CacheCompensationMetrics metrics) {
        String reason = classify(exception);
        metrics.failure(reason);
        deadLetterRecoverer.accept(record, exception);
    }

    private String classify(Throwable throwable) {
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
}

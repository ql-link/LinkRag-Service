package com.qingluo.link.service.mq.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.qingluo.link.service.support.CacheCompensationMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;

@ExtendWith(MockitoExtension.class)
class CacheCompensationKafkaConfigTest {

    private final CacheCompensationKafkaConfig config = new CacheCompensationKafkaConfig();

    @Mock private ConsumerRecordRecoverer deadLetterRecoverer;
    @Mock private CacheCompensationMetrics metrics;

    @Test
    void unknownTarget_isPublishedToDltAndCountedSeparately() {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.cache.evict", 1, 9L, "key",
                "{\"cache_target\":\"legacy\"}");
        IllegalArgumentException failure =
            new IllegalArgumentException("Unknown cache target: legacy");

        config.recover(record, failure, deadLetterRecoverer, metrics);

        verify(deadLetterRecoverer).accept(record, failure);
        verify(metrics).recordConsumeFailure("legacy", "UNKNOWN_TARGET");
        verify(metrics).recordDlt("legacy", "UNKNOWN_TARGET");
    }

    @Test
    void malformedPayload_isNotClassifiedAsDeleteFailure() {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.cache.evict", 0, 1L, "key", "payload");
        DeserializationException failure =
            new DeserializationException("bad", new byte[0], false, new RuntimeException("json"));

        config.recover(record, failure, deadLetterRecoverer, metrics);

        verify(deadLetterRecoverer).accept(record, failure);
        verify(metrics).recordConsumeFailure("unknown", "BAD_PAYLOAD");
        verify(metrics).recordDlt("unknown", "BAD_PAYLOAD");
    }

    @Test
    void errorHandler_isConfigured() {
        assertThat(config.errorHandler(deadLetterRecoverer, metrics)).isNotNull();
        assertThat(CacheCompensationKafkaConfig.MAX_DELIVERIES).isEqualTo(3);
        assertThat(CacheCompensationKafkaConfig.MAX_RETRIES).isEqualTo(2);
    }

    @Test
    void dltMetric_isRecordedOnlyAfterDltPublishSucceeds() {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.cache.evict", 0, 2L, "key",
                "{\"cache_target\":\"llm_runtime_config\"}");
        IllegalStateException failure = new IllegalStateException("redis down");
        org.mockito.Mockito.doThrow(new IllegalStateException("dlt down"))
            .when(deadLetterRecoverer).accept(record, failure);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            config.recover(record, failure, deadLetterRecoverer, metrics))
            .hasMessage("dlt down");

        verify(metrics, never()).recordDlt(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void retryListener_recordsEveryFailedDelivery() {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.cache.evict", 0, 3L, "key",
                "{\"cache_target\":\"llm_runtime_config\"}");

        org.springframework.kafka.listener.RetryListener listener = config.retryListener(metrics);
        IllegalStateException failure = new IllegalStateException("redis down");

        listener.failedDelivery(record, failure, 1);
        listener.failedDelivery(record, failure, 2);
        listener.failedDelivery(record, failure, 3);

        verify(metrics, org.mockito.Mockito.times(3))
            .recordConsumeFailure("llm_runtime_config", "DELETE_EXHAUSTED");
    }
}

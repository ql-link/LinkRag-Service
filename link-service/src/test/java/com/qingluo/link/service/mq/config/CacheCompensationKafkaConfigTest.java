package com.qingluo.link.service.mq.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.qingluo.link.service.cache.replay.CacheReplayEventService;
import com.qingluo.link.service.cache.replay.CacheReplayFailureRecorder;
import com.qingluo.link.service.support.CacheCompensationMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.serializer.DeserializationException;

@ExtendWith(MockitoExtension.class)
class CacheCompensationKafkaConfigTest {

    private final CacheCompensationKafkaConfig config = new CacheCompensationKafkaConfig();

    @Mock private CacheReplayFailureRecorder recorder;
    @Mock private CacheCompensationMetrics metrics;

    @Test
    void unknownTarget_isPersistedAndCountedSeparately() {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.cache.evict", 1, 9L, "key", "payload");
        IllegalArgumentException failure =
            new IllegalArgumentException("Unknown cache target: legacy");

        config.recover(record, failure, recorder, metrics);

        verify(recorder).record(
            CacheReplayEventService.STAGE_CACHE_COMPENSATION,
            record,
            "UNKNOWN_TARGET",
            failure);
        verify(metrics).failure("UNKNOWN_TARGET");
    }

    @Test
    void malformedPayload_isNotClassifiedAsDeleteFailure() {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.cache.evict", 0, 1L, "key", "payload");
        DeserializationException failure =
            new DeserializationException("bad", new byte[0], false, new RuntimeException("json"));

        config.recover(record, failure, recorder, metrics);

        verify(recorder).record(
            CacheReplayEventService.STAGE_CACHE_COMPENSATION,
            record,
            "BAD_PAYLOAD",
            failure);
        verify(metrics).failure("BAD_PAYLOAD");
    }

    @Test
    void errorHandler_isConfigured() {
        assertThat(config.errorHandler(recorder, metrics)).isNotNull();
    }
}

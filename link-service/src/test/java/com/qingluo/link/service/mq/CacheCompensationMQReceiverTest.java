package com.qingluo.link.service.mq;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CacheCompensationMQReceiverTest {

    @Mock private CacheConsistencyService cacheConsistencyService;

    @Test
    void duplicateLlmEvictionMessage_remainsAnIdempotentRepeatedInvalidation() {
        CacheCompensationMQReceiver receiver =
            new CacheCompensationMQReceiver(cacheConsistencyService);
        CacheCompensationMQ.MsgPayload payload = new CacheCompensationMQ.MsgPayload(
            "evt-1", "llm_runtime_config", "10001", "llm_model_config",
            "UPDATE", "trace-1", "2026-07-17T18:00:00+08:00");

        assertThatCode(() -> {
            receiver.receive(payload);
            receiver.receive(payload);
        }).doesNotThrowAnyException();

        verify(cacheConsistencyService, times(2))
            .evictCompensation(CacheEvictTarget.LLM_RUNTIME_CONFIG, "10001");
    }
}

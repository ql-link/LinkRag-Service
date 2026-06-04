package com.qingluo.link.service.mq;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CacheCompensationMQReceiverTest {

    @Mock
    private CacheConsistencyService cacheConsistencyService;

    @InjectMocks
    private CacheCompensationMQReceiver cacheCompensationMQReceiver;

    @Test
    @DisplayName("Should_DelegateToEvictCompensation")
    void Should_DelegateToEvictCompensation() {
        CacheCompensationMQ.MsgPayload payload = new CacheCompensationMQ.MsgPayload();
        payload.setCacheTarget(CacheEvictTarget.SYSTEM_PROVIDER.getCode());
        payload.setRouteId("openai");

        cacheCompensationMQReceiver.receive(payload);

        verify(cacheConsistencyService).evictCompensation(CacheEvictTarget.SYSTEM_PROVIDER, "openai");
    }
}

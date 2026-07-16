package com.qingluo.link.service.mq.kafka;

import com.qingluo.link.service.mq.CacheCompensationMQ;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CacheCompensationKafkaReceiverTest {

    @Mock
    private CacheCompensationMQ.MQReceiver receiver;

    @InjectMocks
    private CacheCompensationKafkaReceiver kafkaReceiver;

    @Test
    @DisplayName("历史业务缓存目标已下线时拒绝分发")
    void shouldRejectRetiredBusinessCacheTarget() {
        assertThatThrownBy(() -> kafkaReceiver.receive("""
                {"event_id":"evt-1","cache_target":"user","route_id":"1001","source_table":"sys_user","operation_type":"UPDATE","trace_id":"trace-1","occurred_at":"2026-05-06T12:00:00+08:00"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown cache target: user");

        verify(receiver, never()).receive(org.mockito.ArgumentMatchers.any());
    }
}

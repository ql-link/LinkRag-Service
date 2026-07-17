package com.qingluo.link.service.cache.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.CacheReplayEventMapper;
import com.qingluo.link.model.dto.entity.CacheReplayEvent;
import com.qingluo.link.model.enums.CacheReplayStatus;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import com.qingluo.link.service.mq.cdc.CdcBridgeService;
import com.qingluo.link.service.mq.cdc.CdcSourceIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class CacheReplayEventServiceTest {

    @Mock private CacheReplayEventMapper mapper;
    @Mock private ObjectProvider<CdcBridgeService> cdcBridgeServiceProvider;
    @Mock private ObjectProvider<CacheCompensationMQ.MQReceiver> compensationReceiverProvider;
    @Mock private CdcBridgeService cdcBridgeService;

    private CacheReplayEventService service;

    @BeforeEach
    void setUp() {
        service = new CacheReplayEventService(
            mapper, cdcBridgeServiceProvider, compensationReceiverProvider);
    }

    @Test
    void recordFailure_persistsStableSourceIdentityAndRawPayload() {
        when(mapper.selectOne(any())).thenReturn(null);

        service.recordFailure(
            CacheReplayEventService.STAGE_CDC_BRIDGE,
            "tolink.canal.binlog",
            2,
            81L,
            "{\"table\":\"dataset_parse_config\"}",
            "ROW_ARRAY_EMPTY",
            new IllegalArgumentException("empty rows"));

        ArgumentCaptor<CacheReplayEvent> captor = ArgumentCaptor.forClass(CacheReplayEvent.class);
        verify(mapper).insert(captor.capture());
        CacheReplayEvent saved = captor.getValue();
        assertThat(saved.getEventKey())
            .isEqualTo("CDC_BRIDGE:tolink.canal.binlog:2:81");
        assertThat(saved.getRawPayload()).contains("dataset_parse_config");
        assertThat(saved.getFailureReason()).isEqualTo("ROW_ARRAY_EMPTY");
        assertThat(saved.getStatus()).isEqualTo(CacheReplayStatus.PENDING.name());
        assertThat(saved.getFailCount()).isEqualTo(1);
    }

    @Test
    void replayCdcEvent_usesOriginalSourceIdentityThenMarksReplayed() {
        CacheReplayEvent pending = pendingEvent(CacheReplayEventService.STAGE_CDC_BRIDGE);
        when(mapper.selectById(10L)).thenReturn(pending);
        when(cdcBridgeServiceProvider.getIfAvailable()).thenReturn(cdcBridgeService);
        when(cdcBridgeService.handle(
            eq(pending.getRawPayload()),
            eq(new CdcSourceIdentity("source-topic", 3, 99L)))).thenReturn(1);

        service.replay(10L, 7L);

        ArgumentCaptor<CacheReplayEvent> captor = ArgumentCaptor.forClass(CacheReplayEvent.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CacheReplayStatus.REPLAYED.name());
        assertThat(captor.getValue().getReplayedAt()).isNotNull();
    }

    @Test
    void terminalEvent_cannotBeReplayedAgain() {
        CacheReplayEvent replayed = pendingEvent(CacheReplayEventService.STAGE_CDC_BRIDGE);
        replayed.setStatus(CacheReplayStatus.REPLAYED.name());
        when(mapper.selectById(10L)).thenReturn(replayed);

        assertThatThrownBy(() -> service.replay(10L, 7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("缓存重放记录状态不允许");
    }

    private CacheReplayEvent pendingEvent(String stage) {
        CacheReplayEvent event = new CacheReplayEvent();
        event.setId(10L);
        event.setEventKey(stage + ":source-topic:3:99");
        event.setStage(stage);
        event.setSourceTopic("source-topic");
        event.setPartitionNo(3);
        event.setSourceOffset(99L);
        event.setRawPayload("{\"table\":\"dataset_parse_config\"}");
        event.setStatus(CacheReplayStatus.PENDING.name());
        event.setFailCount(1);
        return event;
    }
}

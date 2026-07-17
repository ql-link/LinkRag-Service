package com.qingluo.link.service.mq.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.redis.config.CacheConsistencyProperties;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class CdcBridgeServiceTest {

    @Mock private ObjectProvider<MQSend> mqSendProvider;
    @Mock private MQSend mqSend;
    private CdcBridgeService service;

    @BeforeEach
    void setUp() {
        CacheConsistencyProperties properties = new CacheConsistencyProperties();
        properties.getCdc().setMappingsEnabled(true);
        properties.getCdc().setDatabase("tolink_rag_db");
        service = new CdcBridgeService(mqSendProvider, new CdcCacheEvictMapping(), properties);
    }

    @Test
    void datasetRoute_usesBeforeAndAfterValuesWithStableEventIds() {
        when(mqSendProvider.getIfAvailable()).thenReturn(mqSend);
        String event = canal("dataset_parse_config", "UPDATE",
            List.of(Map.of("dataset_id", "41")),
            List.of(Map.of("dataset_id", "40")));

        int sent = service.handle(event, new CdcSourceIdentity("binlog", 2, 99L));

        assertThat(sent).isEqualTo(2);
        ArgumentCaptor<AbstractMQ> captor = ArgumentCaptor.forClass(AbstractMQ.class);
        verify(mqSend, org.mockito.Mockito.times(2)).sendConfirmed(captor.capture());
        List<CacheCompensationMQ.MsgPayload> payloads = captor.getAllValues().stream()
            .map(AbstractMQ::getMessage)
            .map(CacheCompensationMQ::parseMsg)
            .toList();
        assertThat(payloads).extracting(CacheCompensationMQ.MsgPayload::getRouteId)
            .containsExactly("41", "40");
        assertThat(payloads).extracting(CacheCompensationMQ.MsgPayload::getEventId)
            .allMatch(id -> id.startsWith("binlog:2:99:0:dataset_parse_config:"));
    }

    @Test
    void lastLoginOnlyUpdate_isIntentionallyIgnored() {
        int sent = service.handle(canal("sys_user", "UPDATE",
            List.of(Map.of("id", "20", "last_login_at", "new")),
            List.of(Map.of("last_login_at", "old", "updated_at", "old"))));
        assertThat(sent).isZero();
        verify(mqSend, never()).sendConfirmed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mappedEventWithoutRoute_failsInsteadOfSkipping() {
        assertThatThrownBy(() -> service.handle(canal(
            "dataset_parse_config", "UPDATE", List.of(Map.of("id", "1")), List.of())))
            .isInstanceOf(CdcEventException.class)
            .extracting("reason")
            .isEqualTo("ROUTE_MISSING");
    }

    @Test
    void malformedCanalMessage_failsValidation() {
        assertThatThrownBy(() -> service.handle("not-json"))
            .isInstanceOf(CdcEventException.class);
    }

    @Test
    void confirmedSendFailure_propagatesToKafkaConsumerRetry() {
        when(mqSendProvider.getIfAvailable()).thenReturn(mqSend);
        doThrow(new IllegalStateException("broker rejected"))
            .when(mqSend).sendConfirmed(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.handle(canal(
            "dataset_parse_config",
            "UPDATE",
            List.of(Map.of("dataset_id", "10")),
            List.of())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("broker rejected");
    }

    @Test
    void duplicateRowsForSameTarget_areSentOnce() {
        when(mqSendProvider.getIfAvailable()).thenReturn(mqSend);

        int sent = service.handle(canal(
            "dataset_parse_config",
            "UPDATE",
            List.of(Map.of("dataset_id", "10"), Map.of("dataset_id", "10")),
            List.of(Map.of(), Map.of())));

        assertThat(sent).isEqualTo(1);
        verify(mqSend).sendConfirmed(org.mockito.ArgumentMatchers.any());
    }

    private String canal(String table, String type,
                         List<Map<String, String>> data,
                         List<Map<String, String>> old) {
        return JSON.toJSONString(Map.of(
            "database", "tolink_rag_db",
            "table", table,
            "type", type,
            "isDdl", false,
            "es", 1749523200000L,
            "data", data,
            "old", old));
    }
}

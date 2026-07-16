package com.qingluo.link.service.mq.cdc;

import com.alibaba.fastjson.JSON;
import com.qingluo.link.components.mq.MQSend;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CdcBridgeServiceTest {

    @Mock private ObjectProvider<MQSend> mqSendProvider;
    @Mock private MQSend mqSend;
    private CdcCacheEvictMapping mapping;
    private CdcBridgeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mapping = new CdcCacheEvictMapping();
        service = new CdcBridgeService(mqSendProvider, mapping);
    }

    @Test
    void historicalBusinessTables_haveNoCacheEvictionRules() {
        for (String table : List.of("sys_user", "llm_user_config", "llm_system_provider", "llm_provider_model")) {
            assertThat(mapping.rulesOf(table)).isEmpty();
            service.handle(canal(table));
        }
        verify(mqSendProvider, never()).getIfAvailable();
        verify(mqSend, never()).send(any());
    }

    @Test
    void malformedCanalMessage_stillFailsValidation() {
        assertThatThrownBy(() -> service.handle("not-json"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private String canal(String table) {
        return JSON.toJSONString(Map.of(
            "table", table,
            "type", "UPDATE",
            "isDdl", false,
            "es", 1749523200000L,
            "data", List.of(Map.of("id", "1"))));
    }
}

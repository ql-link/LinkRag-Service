package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.model.UsageReportMQ;
import com.qingluo.link.mapper.UsageLogMapper;
import com.qingluo.link.model.dto.entity.UsageLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 用量上报落库单测：字段映射、可空字段落 NULL、status 缺省补 success。
 */
@ExtendWith(MockitoExtension.class)
class UsageReportPersistenceServiceImplTest {

    @Mock
    private UsageLogMapper usageLogMapper;

    @InjectMocks
    private UsageReportPersistenceServiceImpl service;

    private UsageReportMQ.MsgPayload base() {
        UsageReportMQ.MsgPayload p = new UsageReportMQ.MsgPayload();
        p.setUserId(1001L);
        p.setProviderType("openai");
        p.setModelName("text-embedding-3-large");
        p.setStage("parse");
        p.setOperation("embed");
        p.setPromptTokens(12840);
        p.setCompletionTokens(0);
        p.setTotalTokens(12840);
        return p;
    }

    @Test
    @DisplayName("Should_MapAllFields_When_ParseEmbedWithConfig")
    void Should_MapAllFields() {
        UsageReportMQ.MsgPayload p = base();
        p.setConfigId(555L);
        p.setTaskId("task-1");
        p.setStatus("success");

        service.persist(p);

        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        UsageLog usage = captor.getValue();
        assertThat(usage.getUserId()).isEqualTo(1001L);
        assertThat(usage.getConfigId()).isEqualTo(555L);
        assertThat(usage.getProviderType()).isEqualTo("openai");
        assertThat(usage.getModelName()).isEqualTo("text-embedding-3-large");
        assertThat(usage.getStage()).isEqualTo("parse");
        assertThat(usage.getOperation()).isEqualTo("embed");
        assertThat(usage.getPromptTokens()).isEqualTo(12840);
        assertThat(usage.getCompletionTokens()).isZero();
        assertThat(usage.getTotalTokens()).isEqualTo(12840);
        assertThat(usage.getStatus()).isEqualTo("success");
    }

    @Test
    @DisplayName("Should_LeaveNullableColumnsNull_When_RecallSystemConfig")
    void Should_LeaveNull_When_SystemConfig() {
        UsageReportMQ.MsgPayload p = base();
        p.setStage("recall");
        p.setConversationId(7788L);
        // config_id / request_id / latency_ms / status 缺省

        service.persist(p);

        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        UsageLog usage = captor.getValue();
        assertThat(usage.getConfigId()).isNull();
        assertThat(usage.getRequestId()).isNull();
        assertThat(usage.getLatencyMs()).isNull();
        assertThat(usage.getConversationId()).isEqualTo(7788L);
        // status 缺省补 success
        assertThat(usage.getStatus()).isEqualTo("success");
    }

    @Test
    @DisplayName("Should_DefaultTokensToZero_When_Null")
    void Should_DefaultTokensZero() {
        UsageReportMQ.MsgPayload p = base();
        p.setPromptTokens(null);
        p.setCompletionTokens(null);
        p.setTotalTokens(null);

        service.persist(p);

        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        verify(usageLogMapper).insert(captor.capture());
        UsageLog usage = captor.getValue();
        assertThat(usage.getPromptTokens()).isZero();
        assertThat(usage.getCompletionTokens()).isZero();
        assertThat(usage.getTotalTokens()).isZero();
    }
}

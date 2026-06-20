package com.qingluo.link.service.impl;

import com.qingluo.link.components.mq.model.UsageReportMQ;
import com.qingluo.link.core.util.NumberUtil;
import com.qingluo.link.mapper.UsageLogMapper;
import com.qingluo.link.model.dto.entity.UsageLog;
import com.qingluo.link.service.UsageReportPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 全链路用量上报落库实现。
 *
 * <p>语义为旁路、最终一致：每条上报落 {@code llm_usage_log} 一行，与对话最终生成（chat_turn 通道）口径一致。</p>
 *
 * <p>NULL 是合法态：{@code config_id}（系统配置调用如召回 query 编码）/ {@code conversation_id} /
 * {@code request_id} / {@code latency_ms} 缺省即落 NULL，不补默认值。{@code completion_tokens}
 * 对向量类（embed/rerank）恒为 0 是预期值。{@code task_id} 当前表无独立列，仅作审计锚点不落库。</p>
 *
 * <p>幂等：本通道默认 at-least-once、偶发重复可接受（旁路账本）；未启用强去重以免与 Python 侧 schema 漂移，
 * 信封 {@code message_id} 仅用于排障追踪。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsageReportPersistenceServiceImpl implements UsageReportPersistenceService {

    private static final String DEFAULT_STATUS = "success";

    private final UsageLogMapper usageLogMapper;

    @Override
    public void persist(UsageReportMQ.MsgPayload payload) {
        UsageLog usage = new UsageLog();
        usage.setUserId(payload.getUserId());
        usage.setConfigId(payload.getConfigId());            // 可空 → NULL（系统配置调用）
        usage.setProviderType(payload.getProviderType());
        usage.setModelName(payload.getModelName());
        usage.setStage(payload.getStage());
        usage.setOperation(payload.getOperation());
        usage.setPromptTokens(NumberUtil.zeroIfNull(payload.getPromptTokens()));
        usage.setCompletionTokens(NumberUtil.zeroIfNull(payload.getCompletionTokens()));
        usage.setTotalTokens(NumberUtil.zeroIfNull(payload.getTotalTokens()));
        usage.setConversationId(payload.getConversationId()); // 可空 → NULL
        usage.setRequestId(payload.getRequestId());           // 可空 → NULL
        usage.setLatencyMs(payload.getLatencyMs());           // 可空 → NULL
        usage.setStatus(StringUtils.hasText(payload.getStatus()) ? payload.getStatus() : DEFAULT_STATUS);
        usageLogMapper.insert(usage);

        log.info("usage_report persisted, user_id={}, stage={}, operation={}, model={}, total_tokens={}, task_id={}",
                payload.getUserId(), payload.getStage(), payload.getOperation(),
                payload.getModelName(), usage.getTotalTokens(), payload.getTaskId());
    }
}

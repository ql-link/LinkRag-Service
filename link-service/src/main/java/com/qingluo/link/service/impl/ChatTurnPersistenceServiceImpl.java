package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.components.mq.constant.ChatTurnStatus;
import com.qingluo.link.components.mq.constant.UsageOperation;
import com.qingluo.link.components.mq.constant.UsageStage;
import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.core.util.NumberUtil;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.ChatMessageMapper;
import com.qingluo.link.mapper.UsageLogMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.ChatMessage;
import com.qingluo.link.model.dto.entity.UsageLog;
import com.qingluo.link.service.ChatTurnPersistenceService;
import com.qingluo.link.service.ConversationTitleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 对话轮次落库实现。
 *
 * <p>消费侧约束：</p>
 * <ul>
 *   <li><b>按 turn_id upsert</b>：一轮 = 「起点 GENERATING + 终态」至少两条同 {@code turn_id} 的消息。
 *       起点插入「生成中」行，终态更新同一行。匹配键为 {@code (conversation_id, turn_id)}，
 *       既防跨会话覆盖（turn_id 由客户端提供、唯一索引全局），又天然幂等。</li>
 *   <li><b>状态不回退</b>：终态写入后不再被迟到/重投的 {@code GENERATING} 覆盖；重复终态视为重投跳过。</li>
 *   <li><b>归属校验</b>：conversation_id 来自前端请求体、user_id 取自 token，Python 仅透传不校验；
 *       落库前必须校验 conversation 属于该 user，不匹配直接丢弃并告警，防止跨用户写入。</li>
 *   <li><b>用量入账</b>：{@code llm_usage_log} 仅在转入终态时写一次（GENERATING 无 token，不入账），避免重复计费。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatTurnPersistenceServiceImpl implements ChatTurnPersistenceService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final UsageLogMapper usageLogMapper;
    private final ConversationTitleService conversationTitleService;

    /** 创建对话时的默认标题，首轮落库时用提问替换。 */
    private static final String DEFAULT_TITLE = "新对话";

    @Override
    @Transactional
    public void persist(ChatTurnMQ.MsgPayload payload) {
        ChatTurnStatus incoming = ChatTurnStatus.from(payload.getStatus());

        // 1. 归属校验：conversation 必须属于 payload 中的 user（防跨用户写入）。
        ChatConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, payload.getConversationId()));
        if (conversation == null || !payload.getUserId().equals(conversation.getUserId())) {
            log.warn("chat_turn ownership mismatch, drop message. conversation_id={}, user_id={}, turn_id={}",
                    payload.getConversationId(), payload.getUserId(), payload.getTurnId());
            return;
        }

        // 2. 按 (conversation_id, turn_id) 定位同一轮的行（防跨会话覆盖，turn_id 唯一索引为全局）。
        ChatMessage existing = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, payload.getConversationId())
                .eq(ChatMessage::getTurnId, payload.getTurnId()));

        if (existing == null) {
            insertTurn(conversation, payload, incoming);
            return;
        }

        // 3. 状态不回退 + 幂等：已是终态的行不再被 GENERATING 或重复终态覆盖。
        if (ChatTurnStatus.from(existing.getStatus()).isTerminal()) {
            log.info("chat_turn already terminal, ignore. conversation_id={}, turn_id={}, existing={}, incoming={}",
                    payload.getConversationId(), payload.getTurnId(), existing.getStatus(), incoming);
            return;
        }
        // 既有为 GENERATING：迟到/重复的 GENERATING 幂等跳过，仅终态推进。
        if (!incoming.isTerminal()) {
            log.info("chat_turn duplicate GENERATING, ignore. conversation_id={}, turn_id={}",
                    payload.getConversationId(), payload.getTurnId());
            return;
        }
        promoteToTerminal(conversation, existing, payload, incoming);
    }

    /**
     * 首次见到该轮：插入新行（GENERATING 起点或迟到终态先到都走这里）。
     */
    private void insertTurn(ChatConversation conversation, ChatTurnMQ.MsgPayload payload, ChatTurnStatus incoming) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(payload.getConversationId());
        message.setTurnId(payload.getTurnId());
        message.setConfigId(payload.getConfigId());
        message.setModelName(payload.getModelName());
        message.setQuery(payload.getQuery());
        message.setAnswer(payload.getAnswer());
        message.setReferences(payload.getReferences());
        message.setRequestId(payload.getRequestId());
        message.setStatus(incoming.name());
        message.setErrorCode(payload.getErrorCode());
        message.setErrorMessage(payload.getErrorMessage());
        messageMapper.insert(message);

        if (incoming.isTerminal()) {
            insertUsageLog(payload, message.getId(), incoming);
        }
        updateConversationMeta(conversation, payload, incoming);
    }

    /**
     * 既有 GENERATING 行推进到终态：更新同一行，并补写一次用量账本。
     */
    private void promoteToTerminal(ChatConversation conversation, ChatMessage existing,
                                   ChatTurnMQ.MsgPayload payload, ChatTurnStatus incoming) {
        existing.setStatus(incoming.name());
        existing.setAnswer(payload.getAnswer());
        existing.setReferences(payload.getReferences());
        existing.setErrorCode(payload.getErrorCode());
        existing.setErrorMessage(payload.getErrorMessage());
        // 终态补齐配置与模型快照（GENERATING 起点可能未解析模型）。
        if (payload.getConfigId() != null) {
            existing.setConfigId(payload.getConfigId());
        }
        if (StringUtils.hasText(payload.getModelName())) {
            existing.setModelName(payload.getModelName());
        }
        if (payload.getRequestId() != null) {
            existing.setRequestId(payload.getRequestId());
        }
        messageMapper.updateById(existing);

        insertUsageLog(payload, existing.getId(), incoming);
        updateConversationMeta(conversation, payload, incoming);
    }

    /**
     * 写入 llm_usage_log（关联 conversation_id / message_id / request_id），仅终态调用。
     */
    private void insertUsageLog(ChatTurnMQ.MsgPayload payload, Long messageId, ChatTurnStatus incoming) {
        UsageLog usage = new UsageLog();
        usage.setUserId(payload.getUserId());
        usage.setConfigId(payload.getConfigId());
        usage.setProviderType(nullToEmpty(payload.getProviderType()));
        usage.setModelName(nullToEmpty(payload.getModelName()));
        // 全链路口径：对话最终生成补 stage=chat / operation=generate，与 usage_report 通道落同一张表口径一致（LINK-184）。
        usage.setStage(UsageStage.CHAT.code());
        usage.setOperation(UsageOperation.GENERATE.code());
        usage.setPromptTokens(NumberUtil.zeroIfNull(payload.getPromptTokens()));
        usage.setCompletionTokens(NumberUtil.zeroIfNull(payload.getCompletionTokens()));
        usage.setTotalTokens(NumberUtil.zeroIfNull(payload.getTotalTokens()));
        usage.setLatencyMs(payload.getLatencyMs());
        // 账本沿用 success/partial/failed 口径：COMPLETED→success，FAILED→failed。
        usage.setStatus(incoming == ChatTurnStatus.FAILED ? "failed" : "success");
        usage.setConversationId(payload.getConversationId());
        usage.setMessageId(messageId);
        usage.setRequestId(payload.getRequestId());
        usageLogMapper.insert(usage);
    }

    /**
     * 更新对话元信息：last_config_id / last_model_name / updated_at；首轮由提问生成临时标题，
     * 终态成功时再异步生成自然短标题（GENERATING/FAILED 仅保留临时标题，不调模型）。
     */
    private void updateConversationMeta(ChatConversation conversation, ChatTurnMQ.MsgPayload payload,
                                        ChatTurnStatus incoming) {
        if (payload.getConfigId() != null) {
            conversation.setLastConfigId(payload.getConfigId());
        }
        if (StringUtils.hasText(payload.getModelName())) {
            conversation.setLastModelName(payload.getModelName());
        }
        String fallbackTitle = applyTitleFromFirstTurn(conversation, payload.getQuery());
        // 置空 updated_at，让 updateById 不写该列，由 DB ON UPDATE CURRENT_TIMESTAMP 自动刷新，
        // 使会话列表「最近活跃倒序」每轮（含 GENERATING 起点）随落库前移（P1）。
        conversation.setUpdatedAt(null);
        conversationMapper.updateById(conversation);
        if (incoming == ChatTurnStatus.COMPLETED && StringUtils.hasText(fallbackTitle)) {
            log.info("chat_turn schedule conversation title generation. conversation_id={}, turn_id={}, fallback_title={}",
                    conversation.getId(), payload.getTurnId(), fallbackTitle);
            conversationTitleService.generateAfterCommit(
                    conversation.getId(), payload.getUserId(), payload.getConfigId(),
                    payload.getQuery(), payload.getAnswer(), fallbackTitle);
        }
    }

    /**
     * 首轮落库时用提问生成临时标题：仅当当前标题为空或仍是默认标题。
     */
    private String applyTitleFromFirstTurn(ChatConversation conversation, String query) {
        String current = conversation.getTitle();
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String title = conversationTitleService.buildFallbackTitle(query);
        if (!StringUtils.hasText(title)) {
            return null;
        }
        boolean needTitle = !StringUtils.hasText(current)
                || DEFAULT_TITLE.equals(current.trim())
                || title.equals(current.trim())
                || isQuestionPrefixTitle(query, current);
        if (!needTitle) {
            return null;
        }
        if (StringUtils.hasText(current)
                && !DEFAULT_TITLE.equals(current.trim())
                && isQuestionPrefixTitle(query, current)) {
            return current.trim();
        }
        conversation.setTitle(title);
        return title;
    }

    private boolean isQuestionPrefixTitle(String query, String currentTitle) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(currentTitle)) {
            return false;
        }
        String normalizedQuery = conversationTitleService.buildFallbackTitle(query);
        String normalizedTitle = currentTitle.trim();
        return StringUtils.hasText(normalizedQuery)
                && normalizedTitle.length() >= 8
                && normalizedQuery.startsWith(normalizedTitle);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 *   <li><b>幂等去重</b>：以 {@code request_id} 判断是否已落库，应对 MQ 重投。
 *       routing_key = conversation_id 保证同对话有序，重投为顺序到达，存在性校验即可去重。</li>
 *   <li><b>归属校验</b>：conversation_id 来自前端请求体、user_id 取自 token，Python 仅透传不校验；
 *       落库前必须校验 conversation 属于该 user，不匹配直接丢弃并告警，防止跨用户写入。</li>
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
        // 1. 幂等去重：request_id 已落库则跳过。
        long persisted = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRequestId, payload.getRequestId()));
        if (persisted > 0) {
            log.info("chat_turn duplicate ignored, request_id={}", payload.getRequestId());
            return;
        }

        // 2. 归属校验：conversation 必须属于 payload 中的 user。
        ChatConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getId, payload.getConversationId()));
        if (conversation == null || !payload.getUserId().equals(conversation.getUserId())) {
            log.warn("chat_turn ownership mismatch, drop message. conversation_id={}, user_id={}, request_id={}",
                    payload.getConversationId(), payload.getUserId(), payload.getRequestId());
            return;
        }

        // 3. 写入 chat_message（一行一轮）。
        ChatMessage message = new ChatMessage();
        message.setConversationId(payload.getConversationId());
        message.setConfigId(payload.getConfigId());
        message.setModelName(payload.getModelName());
        message.setQuery(payload.getQuery());
        message.setAnswer(payload.getAnswer());
        message.setReferences(payload.getReferences());
        message.setRequestId(payload.getRequestId());
        message.setStatus(payload.getStatus());
        messageMapper.insert(message);

        // 4. 写入 llm_usage_log，关联 conversation_id / message_id / request_id。
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
        usage.setStatus(payload.getStatus());
        usage.setConversationId(payload.getConversationId());
        usage.setMessageId(message.getId());
        usage.setRequestId(payload.getRequestId());
        usageLogMapper.insert(usage);

        // 5. 更新对话元信息：last_config_id / last_model_name / updated_at；首轮由提问生成标题。
        conversation.setLastConfigId(payload.getConfigId());
        conversation.setLastModelName(payload.getModelName());
        String fallbackTitle = applyTitleFromFirstTurn(conversation, payload.getQuery());
        conversationMapper.updateById(conversation);
        if (StringUtils.hasText(fallbackTitle)) {
            log.info("chat_turn schedule conversation title generation. conversation_id={}, request_id={}, fallback_title={}",
                    conversation.getId(), payload.getRequestId(), fallbackTitle);
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

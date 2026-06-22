package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.mapper.ChatConversationMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.ChatConversation;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.service.ConversationTitleService;
import com.qingluo.link.service.config.ConversationTitleProperties;
import com.qingluo.link.service.llm.ChatTitleGenerator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 首轮对话标题生成：先用首问截断标题即时展示，再在事务提交后异步调用用户 Chat 模型补全自然标题。
 */
@Service
@Slf4j
@EnableConfigurationProperties(ConversationTitleProperties.class)
public class ConversationTitleServiceImpl implements ConversationTitleService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final String CHAT_CAPABILITY = "CHAT";
    private static final Set<Character> TRIM_QUOTES = Set.of('"', '\'', '“', '”', '‘', '’', '「', '」', '《', '》');

    private final ChatConversationMapper conversationMapper;
    private final UserLLMConfigMapper userLLMConfigMapper;
    private final ChatTitleGenerator chatTitleGenerator;
    private final ConversationTitleProperties properties;
    private final Executor conversationTitleExecutor;

    public ConversationTitleServiceImpl(ChatConversationMapper conversationMapper,
                                        UserLLMConfigMapper userLLMConfigMapper,
                                        ChatTitleGenerator chatTitleGenerator,
                                        ConversationTitleProperties properties,
                                        @Qualifier("conversationTitleExecutor") Executor conversationTitleExecutor) {
        this.conversationMapper = conversationMapper;
        this.userLLMConfigMapper = userLLMConfigMapper;
        this.chatTitleGenerator = chatTitleGenerator;
        this.properties = properties;
        this.conversationTitleExecutor = conversationTitleExecutor;
    }

    @Override
    public String buildFallbackTitle(String query) {
        String title = cleanTitle(query);
        if (!StringUtils.hasText(title)) {
            return DEFAULT_TITLE;
        }
        return limitTitle(title);
    }

    @Override
    public void generateAfterCommit(Long conversationId, Long userId, Long configId,
                                    String query, String answer, String fallbackTitle) {
        if (!properties.isEnabled()) {
            log.info("Conversation title generation disabled, keep fallback title. conversationId={}", conversationId);
            return;
        }
        if (!StringUtils.hasText(query) || !StringUtils.hasText(fallbackTitle)) {
            return;
        }
        Runnable task = () -> generateAndUpdate(conversationId, userId, configId, query, answer, fallbackTitle);
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(task, conversationId);
                }
            });
        } else {
            submit(task, conversationId);
        }
    }

    private void submit(Runnable task, Long conversationId) {
        try {
            log.info("Submit conversation title generation task. conversationId={}", conversationId);
            conversationTitleExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("Conversation title generation rejected, keep fallback title. conversationId={}", conversationId, e);
        }
    }

    void generateAndUpdate(Long conversationId, Long userId, Long configId,
                           String query, String answer, String fallbackTitle) {
        log.info("Run conversation title generation task. conversationId={}, configId={}", conversationId, configId);
        UserLLMConfig config = resolveConfig(userId, configId);
        if (config == null) {
            log.info("Skip conversation title generation because no available CHAT config. conversationId={}, userId={}",
                conversationId, userId);
            return;
        }
        String generated = limitTitle(cleanTitle(chatTitleGenerator.generate(config, query, answer)));
        if (!StringUtils.hasText(generated) || DEFAULT_TITLE.equals(generated) || generated.equals(fallbackTitle)) {
            log.info("Skip conversation title update because generated title is blank/default/same. conversationId={}",
                conversationId);
            return;
        }

        conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
            .eq(ChatConversation::getId, conversationId)
            .eq(ChatConversation::getUserId, userId)
            .set(ChatConversation::getTitle, generated));
        log.info("Conversation title updated by model. conversationId={}, title={}", conversationId, generated);
    }

    private UserLLMConfig resolveConfig(Long userId, Long configId) {
        UserLLMConfig config = null;
        if (configId != null) {
            config = userLLMConfigMapper.selectOne(new LambdaQueryWrapper<UserLLMConfig>()
                .eq(UserLLMConfig::getId, configId)
                .eq(UserLLMConfig::getUserId, userId)
                .eq(UserLLMConfig::getCapability, CHAT_CAPABILITY)
                .eq(UserLLMConfig::getIsActive, true));
        }
        if (config != null) {
            return config;
        }
        return userLLMConfigMapper.selectOne(new LambdaQueryWrapper<UserLLMConfig>()
            .eq(UserLLMConfig::getUserId, userId)
            .eq(UserLLMConfig::getCapability, CHAT_CAPABILITY)
            .eq(UserLLMConfig::getIsDefault, true)
            .eq(UserLLMConfig::getIsActive, true));
    }

    private String cleanTitle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String title = raw.trim()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replaceAll("\\s+", " ")
            .replaceFirst("^[0-9一二三四五六七八九十]+[、.．)）]\\s*", "")
            .trim();
        while (title.length() >= 2
            && TRIM_QUOTES.contains(title.charAt(0))
            && TRIM_QUOTES.contains(title.charAt(title.length() - 1))) {
            title = title.substring(1, title.length() - 1).trim();
        }
        return title;
    }

    private String limitTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        int maxLength = Math.max(1, properties.getMaxLength());
        if (title.length() <= maxLength) {
            return title;
        }
        return title.substring(0, maxLength);
    }
}

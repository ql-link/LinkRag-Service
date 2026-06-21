package com.qingluo.link.service;

/**
 * 对话标题生成服务。
 */
public interface ConversationTitleService {

    /**
     * 构造首轮标题生成前的临时标题。
     */
    String buildFallbackTitle(String query);

    /**
     * 在当前事务提交后提交异步标题生成任务；无事务时直接提交。
     */
    void generateAfterCommit(Long conversationId, Long userId, Long configId, String query, String answer, String fallbackTitle);
}

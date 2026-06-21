package com.qingluo.link.service;

import com.qingluo.link.components.mq.model.ChatTurnMQ;

/**
 * 对话轮次落库服务。
 *
 * <p>消费 {@code tolink.rag.chat_turn} 后，在单事务内写 chat_message、llm_usage_log，
 * 并更新 chat_conversation 元信息。负责幂等去重与归属校验。</p>
 */
public interface ChatTurnPersistenceService {

    /**
     * 落库一轮对话。
     *
     * @param payload 已反序列化并通过最小校验的对话轮次载荷
     */
    void persist(ChatTurnMQ.MsgPayload payload);
}

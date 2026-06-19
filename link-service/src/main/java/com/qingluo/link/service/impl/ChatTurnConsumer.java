package com.qingluo.link.service.impl;

import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.service.ChatTurnPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 对话轮次消费者：接收解包后的载荷并转交落库服务。
 */
@Component
@RequiredArgsConstructor
public class ChatTurnConsumer implements ChatTurnMQ.MQReceiver {

    private final ChatTurnPersistenceService chatTurnPersistenceService;

    @Override
    public void receive(ChatTurnMQ.MsgPayload msgPayload) {
        chatTurnPersistenceService.persist(msgPayload);
    }
}

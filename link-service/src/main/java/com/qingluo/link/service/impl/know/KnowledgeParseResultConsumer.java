package com.qingluo.link.service.impl.know;

import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 知识文件解析结果消费者，负责接收 MQ 消息并转交业务服务处理。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeParseResultConsumer implements KnowledgeParseResultMQ.MQReceiver {

    private final KnowledgeParseResultService knowledgeParseResultService;

    @Override
    /**
     * 接收解析结果消息并转交业务服务处理。
     */
    public void receive(KnowledgeParseResultMQ.MsgPayload msgPayload) {
        knowledgeParseResultService.handleParseResult(msgPayload);
    }
}

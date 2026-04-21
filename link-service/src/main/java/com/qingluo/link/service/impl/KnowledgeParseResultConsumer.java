package com.qingluo.link.service.impl;

import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.service.mq.KnowledgeParseResultMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeParseResultConsumer implements KnowledgeParseResultMQ.MQReceiver {

    private final KnowledgeParseResultService knowledgeParseResultService;

    @Override
    public void receive(KnowledgeParseResultMQ.MsgPayload msgPayload) {
        knowledgeParseResultService.handleParseResult(msgPayload);
    }
}

package com.qingluo.link.service.impl;

import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.service.mq.KnowledgeParseResultMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tolink.knowledge-file.legacy-parse-result-enabled", havingValue = "true")
public class KnowledgeParseResultConsumer implements KnowledgeParseResultMQ.MQReceiver {

    private final KnowledgeParseResultService knowledgeParseResultService;

    @Override
    public void receive(KnowledgeParseResultMQ.MsgPayload msgPayload) {
        knowledgeParseResultService.handleParseResult(msgPayload);
    }
}

package com.qingluo.link.service;

import com.qingluo.link.service.mq.KnowledgeParseResultMQ;

public interface KnowledgeParseResultService {

    void handleParseResult(KnowledgeParseResultMQ.MsgPayload payload);
}

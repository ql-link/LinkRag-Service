package com.qingluo.link.service;

import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;

public interface KnowledgeParseResultService {

    void handleParseResult(KnowledgeParseResultMQ.MsgPayload payload);
}

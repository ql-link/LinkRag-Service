package com.qingluo.link.service;

import com.qingluo.link.components.mq.model.DocumentParseResultMQ;

public interface DocumentParseResultService {

    void handleParseResult(DocumentParseResultMQ.MsgPayload payload);
}

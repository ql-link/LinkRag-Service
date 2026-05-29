package com.qingluo.link.service.impl.know;

import com.qingluo.link.service.DocumentParseResultService;
import com.qingluo.link.components.mq.model.DocumentParseResultMQ;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 知识文件解析结果消费者，负责接收 MQ 消息并转交业务服务处理。
 */
@Component
@RequiredArgsConstructor
public class DocumentParseResultConsumer implements DocumentParseResultMQ.MQReceiver {

    private final DocumentParseResultService documentParseResultService;

    @Override
    /**
     * 接收解析结果消息并转交业务服务处理。
     */
    public void receive(DocumentParseResultMQ.MsgPayload msgPayload) {
        documentParseResultService.handleParseResult(msgPayload);
    }
}

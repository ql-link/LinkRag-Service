package com.qingluo.link.service.impl;

import com.qingluo.link.components.mq.model.UsageReportMQ;
import com.qingluo.link.service.UsageReportPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用量上报消费者：接收解包后的载荷并转交落库服务。
 */
@Component
@RequiredArgsConstructor
public class UsageReportConsumer implements UsageReportMQ.MQReceiver {

    private final UsageReportPersistenceService usageReportPersistenceService;

    @Override
    public void receive(UsageReportMQ.MsgPayload msgPayload) {
        usageReportPersistenceService.persist(msgPayload);
    }
}

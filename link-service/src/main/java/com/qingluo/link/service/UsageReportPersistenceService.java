package com.qingluo.link.service;

import com.qingluo.link.components.mq.model.UsageReportMQ;

/**
 * 全链路用量上报落库服务：将一条用量上报写入 {@code llm_usage_log} 一行。
 */
public interface UsageReportPersistenceService {

    void persist(UsageReportMQ.MsgPayload payload);
}

package com.qingluo.link.service.observability;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 日志查询入参，保持 Controller 与 Loki 查询构造解耦。
 */
@Getter
@AllArgsConstructor
public class LogQueryCriteria {

    private final String service;
    private final String level;
    private final String traceId;
    private final String keyword;
    private final String startTime;
    private final String endTime;
    private final int page;
    private final int pageSize;
}

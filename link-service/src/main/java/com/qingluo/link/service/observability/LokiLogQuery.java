package com.qingluo.link.service.observability;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 已校验、可直接发送给 Loki 的查询。
 */
@Getter
@AllArgsConstructor
public class LokiLogQuery {

    private final String query;
    private final Instant start;
    private final Instant end;
    private final int page;
    private final int pageSize;
    private final int limit;
}

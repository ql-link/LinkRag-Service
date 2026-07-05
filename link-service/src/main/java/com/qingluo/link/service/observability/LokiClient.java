package com.qingluo.link.service.observability;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Loki HTTP API 客户端抽象，便于 service 单测替换。
 */
public interface LokiClient {

    String queryRange(String query, Instant start, Instant end, int limit) throws IOException, InterruptedException;

    List<String> labelValues(String label) throws IOException, InterruptedException;
}

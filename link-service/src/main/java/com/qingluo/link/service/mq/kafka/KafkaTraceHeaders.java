package com.qingluo.link.service.mq.kafka;

import com.qingluo.link.observability.trace.TraceHeaders;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

/**
 * Kafka header reader for cross-service trace propagation.
 */
public final class KafkaTraceHeaders {

    private KafkaTraceHeaders() {
    }

    public static String traceId(Headers headers) {
        if (headers == null) {
            return null;
        }
        for (String name : TraceHeaders.TRACE_ID_HEADER_ALIASES) {
            Header header = headers.lastHeader(name);
            if (header != null && header.value() != null) {
                return new String(header.value(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}

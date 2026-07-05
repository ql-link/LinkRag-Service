package com.qingluo.link.observability.trace;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport header utilities for cross-service trace propagation.
 */
public final class TraceHeaders {

    public static final String TRACE_ID_HEADER = TraceContext.TRACE_ID_HEADER;

    public static final String TRACE_ID_MDC_KEY = TraceContext.TRACE_ID_KEY;

    public static final List<String> TRACE_ID_HEADER_ALIASES = List.of(
            TraceContext.TRACE_ID_HEADER,
            "x-trace-id",
            "trace_id",
            "trace-id"
    );

    private TraceHeaders() {
    }

    /**
     * Returns explicit message headers plus the current MDC trace id when one exists.
     *
     * <p>Explicit trace headers win. This keeps business code free to override the
     * transport value while allowing the common path to work without touching payload models.</p>
     */
    public static Map<String, String> withCurrentTrace(Map<String, String> explicitHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(explicitHeaders)) {
            explicitHeaders.forEach((name, value) -> {
                if (StringUtils.hasText(name) && value != null) {
                    headers.put(name, value);
                }
            });
        }
        if (!hasTraceHeader(headers)) {
            String traceId = TraceContext.currentTraceId();
            if (StringUtils.hasText(traceId)) {
                headers.put(TRACE_ID_HEADER, traceId);
            }
        }
        return headers;
    }

    private static boolean hasTraceHeader(Map<String, String> headers) {
        return headers.keySet().stream()
                .anyMatch(name -> TRACE_ID_HEADER_ALIASES.stream().anyMatch(alias -> alias.equalsIgnoreCase(name)));
    }
}

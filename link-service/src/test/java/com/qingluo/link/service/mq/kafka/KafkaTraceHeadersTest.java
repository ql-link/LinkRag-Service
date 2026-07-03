package com.qingluo.link.service.mq.kafka;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTraceHeadersTest {

    @Test
    void should_read_python_trace_header_aliases() {
        Headers headers = new RecordHeaders()
                .add("trace_id", "trace-python".getBytes(StandardCharsets.UTF_8));

        assertThat(KafkaTraceHeaders.traceId(headers)).isEqualTo("trace-python");
    }
}

package com.qingluo.link.service.mq;

import com.qingluo.link.components.mq.model.UsageReportMQ;
import com.qingluo.link.observability.trace.TraceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UsageReportKafkaReceiverTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_restore_trace_id_from_kafka_header() {
        UsageReportMQ.MQReceiver businessReceiver = mock(UsageReportMQ.MQReceiver.class);
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        doAnswer(invocation -> {
            seenTraceId.set(MDC.get(TraceContext.TRACE_ID_KEY));
            return null;
        }).when(businessReceiver).receive(any(UsageReportMQ.MsgPayload.class));
        UsageReportKafkaReceiver receiver = new UsageReportKafkaReceiver(businessReceiver);

        receiver.receive(record("""
                {"payload":{"user_id":2,"provider_type":"openai","model_name":"gpt-4o-mini","stage":"chat","operation":"generate","prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                """, "trace-from-python"));

        verify(businessReceiver).receive(any(UsageReportMQ.MsgPayload.class));
        assertThat(seenTraceId.get()).isEqualTo("trace-from-python");
        assertThat(MDC.get(TraceContext.TRACE_ID_KEY)).isNull();
    }

    private ConsumerRecord<String, String> record(String body, String traceId) {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(UsageReportMQ.MQ_NAME, 0, 0L, "key", body);
        record.headers().add(TraceContext.TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}

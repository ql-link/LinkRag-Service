package com.qingluo.link.service.mq;

import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.observability.trace.TraceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatTurnKafkaReceiverTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_restore_trace_id_from_kafka_header() {
        ChatTurnMQ.MQReceiver businessReceiver = mock(ChatTurnMQ.MQReceiver.class);
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        doAnswer(invocation -> {
            seenTraceId.set(MDC.get(TraceContext.TRACE_ID_KEY));
            return null;
        }).when(businessReceiver).receive(any(ChatTurnMQ.MsgPayload.class));
        ChatTurnKafkaReceiver receiver = new ChatTurnKafkaReceiver(businessReceiver);

        receiver.receive(record("""
                {"payload":{"conversation_id":1,"turn_id":"turn-1","request_id":"req-1","user_id":2,"status":"GENERATING"}}
                """, "trace-from-python"));

        ArgumentCaptor<ChatTurnMQ.MsgPayload> captor = ArgumentCaptor.forClass(ChatTurnMQ.MsgPayload.class);
        verify(businessReceiver).receive(captor.capture());
        assertThat(captor.getValue().getTurnId()).isEqualTo("turn-1");
        assertThat(seenTraceId.get()).isEqualTo("trace-from-python");
        assertThat(MDC.get(TraceContext.TRACE_ID_KEY)).isNull();
    }

    private ConsumerRecord<String, String> record(String body, String traceId) {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(ChatTurnMQ.MQ_NAME, 0, 0L, "key", body);
        record.headers().add(TraceContext.TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}

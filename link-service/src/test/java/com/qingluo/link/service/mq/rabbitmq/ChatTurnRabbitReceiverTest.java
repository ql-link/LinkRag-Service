package com.qingluo.link.service.mq.rabbitmq;

import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.observability.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatTurnRabbitReceiverTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void restores_trace_id_and_forwards_payload() {
        ChatTurnMQ.MQReceiver businessReceiver = mock(ChatTurnMQ.MQReceiver.class);
        AtomicReference<String> seenTraceId = new AtomicReference<>();
        doAnswer(invocation -> {
            seenTraceId.set(MDC.get(TraceContext.TRACE_ID_KEY));
            return null;
        }).when(businessReceiver).receive(any(ChatTurnMQ.MsgPayload.class));
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-trace-id", "trace-from-python");
        Message message = new Message("""
                {"payload":{"conversation_id":1,"turn_id":"turn-1","request_id":"req-1","user_id":2,"status":"GENERATING"}}
                """.getBytes(StandardCharsets.UTF_8), properties);

        new ChatTurnRabbitReceiver(businessReceiver).receive(message);

        verify(businessReceiver).receive(any(ChatTurnMQ.MsgPayload.class));
        assertThat(seenTraceId.get()).isEqualTo("trace-from-python");
        assertThat(MDC.get(TraceContext.TRACE_ID_KEY)).isNull();
    }
}

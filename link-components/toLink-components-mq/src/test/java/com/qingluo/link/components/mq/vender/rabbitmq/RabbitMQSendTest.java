package com.qingluo.link.components.mq.vender.rabbitmq;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQProperties;
import com.qingluo.link.components.mq.constant.MQSendType;
import com.qingluo.link.observability.trace.TraceHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

class RabbitMQSendTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void sends_to_default_exchange_and_attaches_trace_header() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        acknowledge(template);
        MQProperties properties = new MQProperties();
        MDC.put(TraceHeaders.TRACE_ID_MDC_KEY, "trace-from-java");

        new RabbitMQSend(template, properties).send(new TestMQ());

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(template).convertAndSend(
                eq(""), eq("test.queue"), eq("{}"), processor.capture(),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));
        Message processed = processor.getValue().postProcessMessage(new Message(new byte[0]));
        assertThat((Object) processed.getMessageProperties().getHeader(TraceHeaders.TRACE_ID_HEADER))
                .isEqualTo("trace-from-java");
    }

    @Test
    void rejects_delayed_send_when_optional_plugin_is_disabled() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        MQProperties properties = new MQProperties();
        properties.setRabbitmqDelayedMessageEnabled(false);

        assertThatThrownBy(() -> new RabbitMQSend(template, properties).send(new TestMQ(), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delayed message plugin is not enabled");
    }

    private void acknowledge(RabbitTemplate template) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(MessagePostProcessor.class),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));
    }

    private static class TestMQ implements AbstractMQ {
        @Override public String getMQName() { return "test.queue"; }
        @Override public MQSendType getMQType() { return MQSendType.QUEUE; }
        @Override public String getMessage() { return "{}"; }
        @Override public java.util.Map<String, String> getHeaders() { return Collections.emptyMap(); }
    }
}

package com.qingluo.link.components.mq.vender.kafka;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQSendType;
import com.qingluo.link.observability.trace.TraceHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaMQSendTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_attach_trace_id_header_from_mdc() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaMQSend sender = new KafkaMQSend(kafkaTemplate);
        MDC.put(TraceHeaders.TRACE_ID_MDC_KEY, "trace-from-java");

        sender.send(new TestMQ(Collections.emptyMap()));

        ProducerRecord<String, String> record = sentRecord(kafkaTemplate);
        assertThat(record.topic()).isEqualTo("test.topic");
        assertThat(record.value()).isEqualTo("{\"ok\":true}");
        assertThat(header(record, TraceHeaders.TRACE_ID_HEADER)).isEqualTo("trace-from-java");
    }

    @Test
    void should_preserve_explicit_trace_id_header() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaMQSend sender = new KafkaMQSend(kafkaTemplate);
        MDC.put(TraceHeaders.TRACE_ID_MDC_KEY, "trace-from-mdc");

        sender.send(new TestMQ(Map.of(TraceHeaders.TRACE_ID_HEADER, "trace-explicit")));

        ProducerRecord<String, String> record = sentRecord(kafkaTemplate);
        assertThat(header(record, TraceHeaders.TRACE_ID_HEADER)).isEqualTo("trace-explicit");
    }

    @Test
    void confirmed_send_waits_for_broker_result() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        ListenableFuture<SendResult<String, String>> future = mock(ListenableFuture.class);
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class))).thenReturn(future);
        when(future.get()).thenReturn(mock(SendResult.class));
        KafkaMQSend sender = new KafkaMQSend(kafkaTemplate);

        sender.sendConfirmed(new TestMQ(Collections.emptyMap()));

        verify(future).get();
    }

    @Test
    void confirmed_send_propagates_async_broker_failure() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        ListenableFuture<SendResult<String, String>> future = mock(ListenableFuture.class);
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.any(ProducerRecord.class))).thenReturn(future);
        when(future.get()).thenThrow(new ExecutionException(new RuntimeException("broker rejected")));
        KafkaMQSend sender = new KafkaMQSend(kafkaTemplate);

        assertThatThrownBy(() -> sender.sendConfirmed(new TestMQ(Collections.emptyMap())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("confirmed send failed")
            .hasRootCauseMessage("broker rejected");
    }

    private ProducerRecord<String, String> sentRecord(KafkaTemplate<String, String> kafkaTemplate) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static class TestMQ implements AbstractMQ {
        private final Map<String, String> headers;

        private TestMQ(Map<String, String> headers) {
            this.headers = headers;
        }

        @Override
        public String getMQName() {
            return "test.topic";
        }

        @Override
        public MQSendType getMQType() {
            return MQSendType.QUEUE;
        }

        @Override
        public String getMessage() {
            return "{\"ok\":true}";
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }
    }
}

package com.qingluo.link.components.mq.vender.kafka;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.observability.trace.TraceHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka implementation hidden behind the business-facing MQSend contract.
 */
public class KafkaMQSend implements MQSend {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaMQSend(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(AbstractMQ abstractMQ) {
        validate(abstractMQ);
        ProducerRecord<String, String> record =
                new ProducerRecord<>(abstractMQ.getMQName(), abstractMQ.getMessage());
        addHeaders(record, TraceHeaders.withCurrentTrace(abstractMQ.getHeaders()));
        kafkaTemplate.send(record);
    }

    @Override
    public void send(AbstractMQ abstractMQ, int delay) {
        Assert.isTrue(delay <= 0, "Kafka delayed message is not supported by this template");
        send(abstractMQ);
    }

    private void validate(AbstractMQ abstractMQ) {
        Assert.notNull(abstractMQ, "abstractMQ must not be null");
        Assert.hasText(abstractMQ.getMQName(), "Kafka topic must not be blank");
        Assert.notNull(abstractMQ.getMQType(), "MQ type must not be null");
        Assert.notNull(abstractMQ.getMessage(), "MQ message must not be null");
    }

    private void addHeaders(ProducerRecord<String, String> record, Map<String, String> headers) {
        headers.forEach((name, value) ->
                record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8))));
    }
}

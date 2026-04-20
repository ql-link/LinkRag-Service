package com.qingluo.link.components.mq.vender.kafka;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.Assert;

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
        kafkaTemplate.send(abstractMQ.getMQName(), abstractMQ.getMessage());
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
}

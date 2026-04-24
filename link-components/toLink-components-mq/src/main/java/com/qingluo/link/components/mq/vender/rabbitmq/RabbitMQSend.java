package com.qingluo.link.components.mq.vender.rabbitmq;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQProperties;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.mq.MQSendType;
import java.util.Objects;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.Assert;

/**
 * RabbitMQ implementation hidden behind the business-facing MQSend contract.
 */
public class RabbitMQSend implements MQSend {

    private final RabbitTemplate rabbitTemplate;
    private final MQProperties mqProperties;

    public RabbitMQSend(RabbitTemplate rabbitTemplate, MQProperties mqProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.mqProperties = mqProperties;
    }

    @Override
    public void send(AbstractMQ abstractMQ) {
        validate(abstractMQ);
        if (Objects.equals(MQSendType.BROADCAST, abstractMQ.getMQType())) {
            rabbitTemplate.convertAndSend(
                    mqProperties.getFanoutExchangeNamePrefix() + abstractMQ.getMQName(),
                    "",
                    abstractMQ.getMessage());
            return;
        }
        rabbitTemplate.convertAndSend(abstractMQ.getMQName(), abstractMQ.getMessage());
    }

    @Override
    public void send(AbstractMQ abstractMQ, int delay) {
        validate(abstractMQ);
        Assert.isTrue(delay >= 0, "delay must be greater than or equal to 0");
        if (delay == 0) {
            send(abstractMQ);
            return;
        }
        Assert.isTrue(!Objects.equals(MQSendType.BROADCAST, abstractMQ.getMQType()),
                "delayed broadcast message is not supported");

        rabbitTemplate.convertAndSend(
                mqProperties.getDelayedExchangeName(),
                abstractMQ.getMQName(),
                abstractMQ.getMessage(),
                message -> {
                    message.getMessageProperties().setDelay(Math.toIntExact(delay * 1000L));
                    return message;
                });
    }

    private void validate(AbstractMQ abstractMQ) {
        Assert.notNull(abstractMQ, "abstractMQ must not be null");
        Assert.hasText(abstractMQ.getMQName(), "MQ name must not be blank");
        Assert.notNull(abstractMQ.getMQType(), "MQ type must not be null");
        Assert.notNull(abstractMQ.getMessage(), "MQ message must not be null");
    }
}

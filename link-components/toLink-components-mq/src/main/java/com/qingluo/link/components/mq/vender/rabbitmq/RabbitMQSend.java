package com.qingluo.link.components.mq.vender.rabbitmq;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQProperties;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.mq.constant.MQSendType;
import com.qingluo.link.observability.trace.TraceHeaders;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
        MessagePostProcessor headers = headersPostProcessor(abstractMQ);
        if (Objects.equals(MQSendType.BROADCAST, abstractMQ.getMQType())) {
            publishConfirmed(
                    mqProperties.getFanoutExchangeNamePrefix() + abstractMQ.getMQName(),
                    "",
                    abstractMQ.getMessage(),
                    headers);
            return;
        }
        publishConfirmed("", abstractMQ.getMQName(), abstractMQ.getMessage(), headers);
    }

    @Override
    public void send(AbstractMQ abstractMQ, int delay) {
        validate(abstractMQ);
        Assert.isTrue(delay >= 0, "delay must be greater than or equal to 0");
        if (delay == 0) {
            send(abstractMQ);
            return;
        }
        Assert.state(mqProperties.isRabbitmqDelayedMessageEnabled(),
                "RabbitMQ delayed message plugin is not enabled");
        Assert.isTrue(!Objects.equals(MQSendType.BROADCAST, abstractMQ.getMQType()),
                "delayed broadcast message is not supported");

        publishConfirmed(
                mqProperties.getDelayedExchangeName(),
                abstractMQ.getMQName(),
                abstractMQ.getMessage(),
                message -> {
                    TraceHeaders.withCurrentTrace(abstractMQ.getHeaders())
                            .forEach((name, value) -> message.getMessageProperties().setHeader(name, value));
                    message.getMessageProperties().setDelay(Math.toIntExact(delay * 1000L));
                    return message;
                });
    }

    @Override
    public void sendConfirmed(AbstractMQ abstractMQ) {
        send(abstractMQ);
    }

    private void publishConfirmed(
            String exchange,
            String routingKey,
            String payload,
            MessagePostProcessor postProcessor) {
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, postProcessor, correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(10, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "missing confirm" : confirm.getReason();
                throw new IllegalStateException("RabbitMQ broker rejected message: " + reason);
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned unroutable message");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RabbitMQ confirmed send failed", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("RabbitMQ confirmed send failed", ex);
        }
    }

    private void validate(AbstractMQ abstractMQ) {
        Assert.notNull(abstractMQ, "abstractMQ must not be null");
        Assert.hasText(abstractMQ.getMQName(), "MQ name must not be blank");
        Assert.notNull(abstractMQ.getMQType(), "MQ type must not be null");
        Assert.notNull(abstractMQ.getMessage(), "MQ message must not be null");
    }

    private MessagePostProcessor headersPostProcessor(AbstractMQ abstractMQ) {
        Map<String, String> headers = TraceHeaders.withCurrentTrace(abstractMQ.getHeaders());
        return message -> {
            headers.forEach((name, value) -> message.getMessageProperties().setHeader(name, value));
            return message;
        };
    }
}

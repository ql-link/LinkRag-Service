package com.qingluo.link.service.mq.rabbitmq;

import com.qingluo.link.components.mq.constant.MQVenderChoose;
import com.qingluo.link.observability.trace.TraceContext;
import com.qingluo.link.observability.trace.TraceHeaders;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** RabbitMQ adapter for cache compensation messages. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.RABBIT_MQ)
@ConditionalOnBean(CacheCompensationMQ.MQReceiver.class)
@Slf4j
public class CacheCompensationRabbitReceiver {

    private final CacheCompensationMQ.MQReceiver receiver;

    @RabbitListener(queues = CacheCompensationMQ.MQ_NAME)
    public void receive(Message message) {
        TraceContext.start(TraceHeaders.traceId(message.getMessageProperties().getHeaders()));
        try {
            log.info("收到缓存补偿 RabbitMQ 消息");
            receiver.receive(CacheCompensationMQ.parseMsg(new String(message.getBody(), StandardCharsets.UTF_8)));
        } finally {
            TraceContext.clear();
        }
    }
}

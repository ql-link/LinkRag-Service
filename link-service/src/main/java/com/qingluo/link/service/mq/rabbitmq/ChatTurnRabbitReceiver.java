package com.qingluo.link.service.mq.rabbitmq;

import com.qingluo.link.components.mq.constant.MQVenderChoose;
import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.observability.trace.TraceContext;
import com.qingluo.link.observability.trace.TraceHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** RabbitMQ adapter for chat turn persistence messages. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.RABBIT_MQ)
@Slf4j
public class ChatTurnRabbitReceiver {

    private final ChatTurnMQ.MQReceiver receiver;

    @RabbitListener(queues = ChatTurnMQ.MQ_NAME)
    public void receive(Message message) {
        TraceContext.start(TraceHeaders.traceId(message.getMessageProperties().getHeaders()));
        try {
            log.info("Receive chat turn RabbitMQ message");
            receiver.receive(ChatTurnMQ.parseMsg(new String(message.getBody(), StandardCharsets.UTF_8)));
        } finally {
            TraceContext.clear();
        }
    }
}

package com.qingluo.link.service.mq.rabbitmq;

import com.qingluo.link.components.mq.constant.MQVenderChoose;
import com.qingluo.link.components.mq.model.UsageReportMQ;
import com.qingluo.link.observability.trace.TraceContext;
import com.qingluo.link.observability.trace.TraceHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** RabbitMQ adapter for LLM usage report messages. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.RABBIT_MQ)
@Slf4j
public class UsageReportRabbitReceiver {

    private final UsageReportMQ.MQReceiver receiver;

    @RabbitListener(queues = UsageReportMQ.MQ_NAME)
    public void receive(Message message) {
        TraceContext.start(TraceHeaders.traceId(message.getMessageProperties().getHeaders()));
        try {
            log.info("Receive usage report RabbitMQ message");
            receiver.receive(UsageReportMQ.parseMsg(new String(message.getBody(), StandardCharsets.UTF_8)));
        } finally {
            TraceContext.clear();
        }
    }
}

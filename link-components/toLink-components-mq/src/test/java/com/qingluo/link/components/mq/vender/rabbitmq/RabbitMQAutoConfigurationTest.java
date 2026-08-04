package com.qingluo.link.components.mq.vender.rabbitmq;

import com.qingluo.link.components.mq.constant.MQProperties;
import com.qingluo.link.components.mq.model.ChatTurnMQ;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RabbitMQAutoConfigurationTest {

    @Test
    void declares_queue_with_matching_dlx_and_dlt_without_delayed_plugin() {
        RabbitMQTopologyScanner scanner = mock(RabbitMQTopologyScanner.class);
        when(scanner.scan(anyList())).thenReturn(List.of(new ChatTurnMQ()));
        MQProperties properties = new MQProperties();
        properties.setRabbitmqDelayedMessageEnabled(false);

        Declarables result = new RabbitMQAutoConfiguration().rabbitMQDeclarables(scanner, properties);
        Collection<Declarable> declarations = result.getDeclarables();

        Queue source = declarations.stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(queue -> queue.getName().equals(ChatTurnMQ.MQ_NAME))
                .findFirst()
                .orElseThrow();
        assertThat(source.getArguments())
                .containsEntry("x-dead-letter-exchange", ChatTurnMQ.MQ_NAME + ".DLX")
                .containsEntry("x-dead-letter-routing-key", ChatTurnMQ.MQ_NAME);
        assertThat(declarations).anySatisfy(item -> {
            assertThat(item).isInstanceOf(Queue.class);
            assertThat(((Queue) item).getName()).isEqualTo(ChatTurnMQ.MQ_NAME + ".DLT");
        });
        assertThat(declarations).anySatisfy(item -> {
            assertThat(item).isInstanceOf(DirectExchange.class);
            assertThat(((DirectExchange) item).getName()).isEqualTo(ChatTurnMQ.MQ_NAME + ".DLX");
        });
        assertThat(declarations).noneMatch(item -> item.getClass().getSimpleName().equals("CustomExchange"));
    }
}

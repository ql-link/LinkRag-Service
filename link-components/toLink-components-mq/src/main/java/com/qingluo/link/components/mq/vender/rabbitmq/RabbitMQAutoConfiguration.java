package com.qingluo.link.components.mq.vender.rabbitmq;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQProperties;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.mq.constant.MQSendType;
import com.qingluo.link.components.mq.constant.MQVenderChoose;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * RabbitMQ auto configuration for the toLink MQ component.
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.RABBIT_MQ)
@EnableConfigurationProperties(MQProperties.class)
public class RabbitMQAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MQSend mqSend(RabbitTemplate rabbitTemplate, MQProperties mqProperties) {
        return new RabbitMQSend(rabbitTemplate, mqProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitMQTopologyScanner rabbitMQTopologyScanner() {
        return new RabbitMQTopologyScanner();
    }

    @Bean
    public Declarables rabbitMQDeclarables(RabbitMQTopologyScanner scanner, MQProperties mqProperties) {
        List<Declarable> declarables = new ArrayList<>();
        CustomExchange delayedExchange = null;
        if (mqProperties.isRabbitmqDelayedMessageEnabled()) {
            delayedExchange = delayedExchange(mqProperties.getDelayedExchangeName());
            declarables.add(delayedExchange);
        }

        List<AbstractMQ> messageModels = scanner.scan(mqProperties.getScanBasePackages());
        for (AbstractMQ messageModel : messageModels) {
            if (!StringUtils.hasText(messageModel.getMQName()) || messageModel.getMQType() == null) {
                log.warn("Skip MQ model [{}], MQ name or MQ type is blank", messageModel.getClass().getName());
                continue;
            }
            String queueName = messageModel.getMQName();
            String dlxName = queueName + ".DLX";
            String dltName = queueName + mqProperties.getRabbitmqDlqSuffix();
            DirectExchange deadLetterExchange = new DirectExchange(dlxName, true, false);
            Queue deadLetterQueue = QueueBuilder.durable(dltName).build();
            Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                    .to(deadLetterExchange)
                    .with(queueName);
            Map<String, Object> queueArguments = new HashMap<>();
            queueArguments.put("x-dead-letter-exchange", dlxName);
            queueArguments.put("x-dead-letter-routing-key", queueName);
            Queue queue = QueueBuilder.durable(queueName)
                    .withArguments(queueArguments)
                    .build();
            declarables.add(deadLetterExchange);
            declarables.add(deadLetterQueue);
            declarables.add(deadLetterBinding);
            declarables.add(queue);

            if (Objects.equals(MQSendType.BROADCAST, messageModel.getMQType())) {
                FanoutExchange fanoutExchange = new FanoutExchange(
                        mqProperties.getFanoutExchangeNamePrefix() + messageModel.getMQName(), true, false);
                Binding binding = BindingBuilder.bind(queue).to(fanoutExchange);
                declarables.add(fanoutExchange);
                declarables.add(binding);
                log.info("Register RabbitMQ broadcast topology: queue={}, exchange={}",
                        messageModel.getMQName(), fanoutExchange.getName());
            } else {
                if (delayedExchange != null) {
                    Binding binding = BindingBuilder.bind(queue)
                            .to(delayedExchange)
                            .with(messageModel.getMQName())
                            .noargs();
                    declarables.add(binding);
                }
                log.info("Register RabbitMQ queue topology: queue={}, dlx={}, dlt={}",
                        queueName, dlxName, dltName);
            }
        }
        return new Declarables(declarables);
    }

    private CustomExchange delayedExchange(String delayedExchangeName) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(delayedExchangeName, "x-delayed-message", true, false, args);
    }
}

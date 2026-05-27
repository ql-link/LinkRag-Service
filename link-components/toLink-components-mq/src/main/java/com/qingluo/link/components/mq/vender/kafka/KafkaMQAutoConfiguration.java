package com.qingluo.link.components.mq.vender.kafka;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.constant.MQProperties;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.mq.constant.MQVenderChoose;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

/**
 * Kafka auto configuration for the toLink MQ component.
 */
@Configuration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.KAFKA)
@EnableConfigurationProperties(MQProperties.class)
public class KafkaMQAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaMQAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MQSend kafkaMQSend(KafkaTemplate<String, String> kafkaTemplate) {
        return new KafkaMQSend(kafkaTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaMQTopologyScanner kafkaMQTopologyScanner() {
        return new KafkaMQTopologyScanner();
    }

    @Bean
    public KafkaAdmin.NewTopics kafkaMQTopics(KafkaMQTopologyScanner scanner, MQProperties mqProperties) {
        if (!mqProperties.isKafkaAutoCreateTopics()) {
            return new KafkaAdmin.NewTopics();
        }

        List<NewTopic> topics = scanner.scan(mqProperties.getScanBasePackages()).stream()
                .filter(model -> StringUtils.hasText(model.getMQName()))
                .map(model -> newTopic(model, mqProperties))
                .collect(Collectors.toList());
        return new KafkaAdmin.NewTopics(topics.toArray(new NewTopic[0]));
    }

    private NewTopic newTopic(AbstractMQ model, MQProperties mqProperties) {
        log.info("Register Kafka topic template: topic={}, partitions={}, replicas={}",
                model.getMQName(), mqProperties.getKafkaTopicPartitions(), mqProperties.getKafkaTopicReplicas());
        return TopicBuilder.name(model.getMQName())
                .partitions(mqProperties.getKafkaTopicPartitions())
                .replicas(mqProperties.getKafkaTopicReplicas())
                .build();
    }
}

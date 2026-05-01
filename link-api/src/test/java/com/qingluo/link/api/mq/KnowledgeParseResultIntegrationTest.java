package com.qingluo.link.api.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.service.mq.KnowledgeParseResultKafkaReceiver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * 三期 parse_result 终态回传链路默认启用。
 *
 * <p>当 MQ vendor 为 Kafka 且业务 Receiver Bean 存在时，结果消费者应作为正式链路挂载。
 */
@SpringBootTest(properties = {
    "qingluopay.mq.vender=kafka",
    "qingluopay.mq.kafka-auto-create-topics=false",
    "spring.kafka.bootstrap-servers=127.0.0.1:19092",
    "spring.kafka.listener.auto-startup=false"
})
@Import(TestSecurityConfig.class)
class KnowledgeParseResultIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void Should_CreateParseResultKafkaReceiver_When_KafkaVendorEnabled() {
        assertThat(applicationContext.getBeansOfType(KnowledgeParseResultKafkaReceiver.class)).hasSize(1);
    }
}

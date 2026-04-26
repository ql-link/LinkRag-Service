package com.qingluo.link.api.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.service.mq.kafka.KnowledgeParseResultKafkaReceiver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * 旧 parse_result Java 写库链路默认关闭。
 *
 * <p>二期由 Python 直接写解析任务和最新解析产物，Java 旧消费者如果默认启动会造成双写。
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
    void Should_NotCreateLegacyParseResultKafkaReceiver_When_LegacySwitchDisabled() {
        assertThat(applicationContext.getBeansOfType(KnowledgeParseResultKafkaReceiver.class)).isEmpty();
        try {
            applicationContext.getBean(KnowledgeParseResultKafkaReceiver.class);
        } catch (NoSuchBeanDefinitionException expected) {
            return;
        }
        throw new AssertionError("legacy parse result receiver should be disabled by default");
    }
}

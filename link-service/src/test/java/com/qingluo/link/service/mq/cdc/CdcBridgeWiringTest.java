package com.qingluo.link.service.mq.cdc;

import com.qingluo.link.service.mq.config.CdcBridgeKafkaConfig;
import com.qingluo.link.service.support.CdcBridgeMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CDC 桥接条件装配测试。
 *
 * <p>消费者 {@link CdcBridgeKafkaReceiver} 与容器工厂 {@link CdcBridgeKafkaConfig} 共用同一条件
 * （{@link CdcBridgeKafkaConfig#CDC_BRIDGE_CONDITION}）：vender=kafka 且 cdc.enabled=true 才装载。
 * 二者口径一致，杜绝“消费者不装但容器工厂仍被创建”的半开状态。</p>
 */
class CdcBridgeWiringTest {

    /** 验证消费者装配。 */
    private final ApplicationContextRunner receiverRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaTestConfig.class, CdcBridgeKafkaReceiver.class);

    /** 验证容器工厂装配（堵半开）。 */
    private final ApplicationContextRunner configRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigStubs.class, CdcBridgeKafkaConfig.class);

    @Test
    @DisplayName("消费者——cdc.enabled 缺省：不装载")
    void receiverNotLoaded_whenCdcEnabledMissing() {
        receiverRunner.withPropertyValues("tolink.mq.vender=kafka")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CdcBridgeKafkaReceiver.class));
    }

    @Test
    @DisplayName("消费者——cdc.enabled=false：不装载")
    void receiverNotLoaded_whenCdcDisabled() {
        receiverRunner.withPropertyValues("tolink.mq.vender=kafka",
                        "tolink.cache-consistency.cdc.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CdcBridgeKafkaReceiver.class));
    }

    @Test
    @DisplayName("消费者——vender 非 kafka：即便 cdc.enabled=true 也不装载")
    void receiverNotLoaded_whenVenderNotKafka() {
        receiverRunner.withPropertyValues("tolink.mq.vender=rabbitMQ",
                        "tolink.cache-consistency.cdc.enabled=true",
                        "tolink.cache-consistency.cdc.source-topic=tolink.canal.binlog")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CdcBridgeKafkaReceiver.class));
    }

    @Test
    @DisplayName("消费者——vender=kafka 且 cdc.enabled=true：装载")
    void receiverLoaded_whenKafkaAndCdcEnabled() {
        receiverRunner.withPropertyValues("tolink.mq.vender=kafka",
                        "tolink.cache-consistency.cdc.enabled=true",
                        "tolink.cache-consistency.cdc.source-topic=tolink.canal.binlog")
                .run(ctx -> assertThat(ctx).hasSingleBean(CdcBridgeKafkaReceiver.class));
    }

    @Test
    @DisplayName("容器工厂——vender=kafka 但 cdc.enabled=false：不装载（堵半开）")
    void factoryNotLoaded_whenCdcDisabled() {
        configRunner.withPropertyValues("tolink.mq.vender=kafka",
                        "tolink.cache-consistency.cdc.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("cdcBridgeKafkaListenerContainerFactory"));
    }

    @Test
    @DisplayName("容器工厂——cdc.enabled 缺省：不装载（堵半开）")
    void factoryNotLoaded_whenCdcEnabledMissing() {
        configRunner.withPropertyValues("tolink.mq.vender=kafka")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("cdcBridgeKafkaListenerContainerFactory"));
    }

    @Test
    @DisplayName("容器工厂——vender=kafka 且 cdc.enabled=true：装载")
    void factoryLoaded_whenKafkaAndCdcEnabled() {
        configRunner.withPropertyValues("tolink.mq.vender=kafka",
                        "tolink.cache-consistency.cdc.enabled=true")
                .run(ctx -> assertThat(ctx).hasBean("cdcBridgeKafkaListenerContainerFactory"));
    }

    @EnableKafka
    @Configuration
    static class KafkaTestConfig {

        @Bean
        CdcBridgeService cdcBridgeService() {
            return Mockito.mock(CdcBridgeService.class);
        }

        // 提供与 @KafkaListener 指定同名的容器工厂（mock），使 endpoint 注册不真正连接 broker
        @Bean("cdcBridgeKafkaListenerContainerFactory")
        @SuppressWarnings({"rawtypes", "unchecked"})
        KafkaListenerContainerFactory cdcBridgeKafkaListenerContainerFactory() {
            KafkaListenerContainerFactory factory = Mockito.mock(KafkaListenerContainerFactory.class);
            MessageListenerContainer container = Mockito.mock(MessageListenerContainer.class);
            Mockito.when(factory.createListenerContainer(Mockito.any())).thenReturn(container);
            return factory;
        }
    }

    /** 为 {@link CdcBridgeKafkaConfig} 的工厂方法提供桩依赖，使条件满足时能真正实例化工厂 Bean。 */
    @Configuration
    static class ConfigStubs {

        @Bean
        @SuppressWarnings("unchecked")
        ConsumerFactory<Object, Object> consumerFactory() {
            return Mockito.mock(ConsumerFactory.class);
        }

        @Bean
        CdcBridgeMetrics cdcBridgeMetrics() {
            return Mockito.mock(CdcBridgeMetrics.class);
        }
    }
}

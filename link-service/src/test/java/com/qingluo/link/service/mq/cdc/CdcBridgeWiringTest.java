package com.qingluo.link.service.mq.cdc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CdcBridgeKafkaReceiver} 条件装配测试。
 *
 * <p>覆盖 acceptance「cdc.enabled 控制桥接消费者是否装载」：vender=kafka 且 cdc.enabled=true 才装载，
 * 缺省 / false / 非 kafka 均不装载，本地/测试零报错。</p>
 */
class CdcBridgeWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaTestConfig.class, CdcBridgeKafkaReceiver.class);

    @Test
    @DisplayName("cdc.enabled 缺省：不装载")
    void notLoaded_whenCdcEnabledMissing() {
        runner.withPropertyValues("tolink.mq.vender=kafka")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CdcBridgeKafkaReceiver.class));
    }

    @Test
    @DisplayName("cdc.enabled=false：不装载")
    void notLoaded_whenCdcDisabled() {
        runner.withPropertyValues("tolink.mq.vender=kafka",
                        "tolink.cache-consistency.cdc.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CdcBridgeKafkaReceiver.class));
    }

    @Test
    @DisplayName("vender 非 kafka：即便 cdc.enabled=true 也不装载")
    void notLoaded_whenVenderNotKafka() {
        runner.withPropertyValues("tolink.mq.vender=rabbitMQ",
                        "tolink.cache-consistency.cdc.enabled=true",
                        "tolink.cache-consistency.cdc.source-topic=tolink.canal.binlog")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(CdcBridgeKafkaReceiver.class));
    }

    @Test
    @DisplayName("vender=kafka 且 cdc.enabled=true：装载")
    void loaded_whenKafkaAndCdcEnabled() {
        runner.withPropertyValues("tolink.mq.vender=kafka",
                        "tolink.cache-consistency.cdc.enabled=true",
                        "tolink.cache-consistency.cdc.source-topic=tolink.canal.binlog")
                .run(ctx -> assertThat(ctx).hasSingleBean(CdcBridgeKafkaReceiver.class));
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
}

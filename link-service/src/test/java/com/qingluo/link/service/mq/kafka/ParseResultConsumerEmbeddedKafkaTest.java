package com.qingluo.link.service.mq.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.qingluo.link.service.exception.NonRetryableParseResultException;
import com.qingluo.link.service.mq.config.ParseResultKafkaConfig;
import com.qingluo.link.service.support.ParseResultMetrics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * parse_result 专用容器工厂的集成验证：
 * <ul>
 *   <li>坏消息（不可重试）被 recover 跳过，不阻塞后续正常消息；</li>
 *   <li>缓存补偿（默认容器工厂）消费不受 parse_result 专用工厂影响。</li>
 * </ul>
 */
@SpringJUnitConfig(ParseResultConsumerEmbeddedKafkaTest.TestConfig.class)
@EmbeddedKafka(partitions = 1, topics = {ParseResultConsumerEmbeddedKafkaTest.PARSE_TOPIC, ParseResultConsumerEmbeddedKafkaTest.CACHE_TOPIC})
class ParseResultConsumerEmbeddedKafkaTest {

    static final String PARSE_TOPIC = "tolink.rag.parse_result";
    static final String CACHE_TOPIC = "tolink.cache.evict";

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private TestListeners listeners;

    @Test
    void Should_NotBlockSubsequentGoodMessage_When_BadMessagePrecedes() throws InterruptedException {
        // 先发坏消息（触发 NonRetryableParseResultException → 立即 recover 跳过），再发正常消息
        kafkaTemplate.send(PARSE_TOPIC, "bad-message");
        kafkaTemplate.send(PARSE_TOPIC, "good-message");

        boolean processed = listeners.goodLatch.await(15, TimeUnit.SECONDS);

        assertThat(processed).as("坏消息之后的正常消息应被处理").isTrue();
        assertThat(listeners.parseProcessed).contains("good-message");
        assertThat(listeners.parseProcessed).doesNotContain("bad-message");
    }

    @Test
    void Should_KeepCacheCompensationConsuming_When_ParseResultUsesDedicatedFactory() throws InterruptedException {
        kafkaTemplate.send(CACHE_TOPIC, "evict-key");

        boolean consumed = listeners.cacheLatch.await(15, TimeUnit.SECONDS);

        assertThat(consumed).as("缓存补偿默认工厂消费不应受影响").isTrue();
    }

    @TestConfiguration
    @EnableKafka
    static class TestConfig {

        @Bean
        ParseResultMetrics parseResultMetrics() {
            // recover 计数不是本集成测试断言对象，用 mock 即可
            return mock(ParseResultMetrics.class);
        }

        @Bean
        ConsumerFactory<Object, Object> consumerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            return new DefaultKafkaConsumerFactory<>(props);
        }

        /** parse_result 专用工厂，复用生产代码的错误处理逻辑。 */
        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> parseResultKafkaListenerContainerFactory(
                ConsumerFactory<Object, Object> consumerFactory, ParseResultMetrics metrics) {
            return new ParseResultKafkaConfig()
                .parseResultKafkaListenerContainerFactory(consumerFactory, metrics);
        }

        /** 默认工厂（缓存补偿等沿用），用于验证隔离。 */
        @Bean
        ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
                ConsumerFactory<Object, Object> consumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            return factory;
        }

        @Bean
        ProducerFactory<String, String> producerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }

        @Bean
        TestListeners testListeners() {
            return new TestListeners();
        }
    }

    @Component
    static class TestListeners {
        final CountDownLatch goodLatch = new CountDownLatch(1);
        final CountDownLatch cacheLatch = new CountDownLatch(1);
        final List<String> parseProcessed = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = PARSE_TOPIC, groupId = "it-parse-result",
            containerFactory = "parseResultKafkaListenerContainerFactory")
        void onParseResult(String msg) {
            if (msg.contains("bad")) {
                // 模拟业务不可恢复消息：错误处理器登记为 not-retryable，立即 recover 跳过
                throw new NonRetryableParseResultException("bad message");
            }
            parseProcessed.add(msg);
            goodLatch.countDown();
        }

        @KafkaListener(topics = CACHE_TOPIC, groupId = "it-cache-evict")
        void onCacheEvict(String msg) {
            cacheLatch.countDown();
        }
    }
}

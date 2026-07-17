package com.qingluo.link.service.mq.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class CacheDeadLetterSupportTest {

    @Test
    void destination_appendsDltSuffixAndLetsKafkaChoosePartition() {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>("tolink.cache.evict", 7, 42L, "key", "payload");

        TopicPartition destination = CacheDeadLetterSupport.destination(
            record, new IllegalStateException("failed"));

        assertThat(destination.topic()).isEqualTo("tolink.cache.evict.DLT");
        assertThat(destination.partition()).isEqualTo(-1);
    }
}

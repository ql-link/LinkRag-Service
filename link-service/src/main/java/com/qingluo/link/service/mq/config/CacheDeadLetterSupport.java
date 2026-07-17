package com.qingluo.link.service.mq.config;

import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

/**
 * Kafka dead-letter support shared by the CDC bridge and cache compensation consumers.
 */
final class CacheDeadLetterSupport {

    static final String DLT_SUFFIX = ".DLT";
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private CacheDeadLetterSupport() {
    }

    static ConsumerRecordRecoverer create(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            CacheDeadLetterSupport::destination);
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(SEND_TIMEOUT);
        return recoverer;
    }

    static TopicPartition destination(ConsumerRecord<?, ?> record, Exception exception) {
        // A negative partition lets Kafka choose a DLT partition. Original topic/partition/offset
        // remain available in the standard dead-letter headers added by the recoverer.
        return new TopicPartition(record.topic() + DLT_SUFFIX, -1);
    }
}

package com.qingluo.link.components.mq;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQ component configuration.
 */
@ConfigurationProperties(prefix = "qingluopay.mq")
public class MQProperties {

    /**
     * Vendor name, for example rabbitMQ.
     */
    private String vender;

    /**
     * Correctly spelled alias for vender.
     */
    private String vendor;

    /**
     * Base packages scanned for AbstractMQ message models.
     */
    private List<String> scanBasePackages = new ArrayList<>();

    /**
     * Global delayed exchange name used by RabbitMQ queue messages.
     */
    private String delayedExchangeName = "delayExchange";

    /**
     * Prefix for fanout exchanges created for broadcast messages.
     */
    private String fanoutExchangeNamePrefix = "fanout_exchange_";

    /**
     * Whether Kafka topics should be declared by KafkaAdmin on startup.
     */
    private boolean kafkaAutoCreateTopics = true;

    /**
     * Default partition count for Kafka topics declared from message models.
     */
    private int kafkaTopicPartitions = 1;

    /**
     * Default replica count for Kafka topics declared from message models.
     */
    private short kafkaTopicReplicas = 1;

    public MQProperties() {
        this.scanBasePackages.add("com.qingluo");
    }

    public String getVender() {
        return vender;
    }

    public void setVender(String vender) {
        this.vender = vender;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public List<String> getScanBasePackages() {
        return scanBasePackages;
    }

    public void setScanBasePackages(List<String> scanBasePackages) {
        this.scanBasePackages = scanBasePackages;
    }

    public String getDelayedExchangeName() {
        return delayedExchangeName;
    }

    public void setDelayedExchangeName(String delayedExchangeName) {
        this.delayedExchangeName = delayedExchangeName;
    }

    public String getFanoutExchangeNamePrefix() {
        return fanoutExchangeNamePrefix;
    }

    public void setFanoutExchangeNamePrefix(String fanoutExchangeNamePrefix) {
        this.fanoutExchangeNamePrefix = fanoutExchangeNamePrefix;
    }

    public boolean isKafkaAutoCreateTopics() {
        return kafkaAutoCreateTopics;
    }

    public void setKafkaAutoCreateTopics(boolean kafkaAutoCreateTopics) {
        this.kafkaAutoCreateTopics = kafkaAutoCreateTopics;
    }

    public int getKafkaTopicPartitions() {
        return kafkaTopicPartitions;
    }

    public void setKafkaTopicPartitions(int kafkaTopicPartitions) {
        this.kafkaTopicPartitions = kafkaTopicPartitions;
    }

    public short getKafkaTopicReplicas() {
        return kafkaTopicReplicas;
    }

    public void setKafkaTopicReplicas(short kafkaTopicReplicas) {
        this.kafkaTopicReplicas = kafkaTopicReplicas;
    }
}

package com.qingluo.link.components.mq;

/**
 * Business-facing MQ message contract.
 */
public interface AbstractMQ {

    /**
     * Queue name or topic name used by the underlying MQ vendor.
     */
    String getMQName();

    /**
     * Sending semantic for the message.
     */
    MQSendType getMQType();

    /**
     * Serialized message body sent to the broker.
     */
    String getMessage();
}

package com.qingluo.link.components.mq;

import com.qingluo.link.components.mq.constant.MQSendType;

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

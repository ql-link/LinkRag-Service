package com.qingluo.link.components.mq.constant;

/**
 * MQ delivery semantics supported by the component.
 */
public enum MQSendType {
    /**
     * Point-to-point queue delivery.
     */
    QUEUE,

    /**
     * Fanout broadcast delivery.
     */
    BROADCAST
}

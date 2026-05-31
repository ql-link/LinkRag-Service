package com.qingluo.link.components.mq;

/**
 * Unified sender used by business code.
 */
public interface MQSend {

    void send(AbstractMQ abstractMQ);

    /**
     * Sends a delayed message.
     *
     * @param delay delay seconds
     */
    void send(AbstractMQ abstractMQ, int delay);
}

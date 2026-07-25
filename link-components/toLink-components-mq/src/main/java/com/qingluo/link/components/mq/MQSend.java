package com.qingluo.link.components.mq;

/**
 * Unified sender used by business code.
 */
public interface MQSend {

    void send(AbstractMQ abstractMQ);

    /**
     * Sends a message and returns only after the underlying broker confirms it when supported.
     *
     * <p>Ordinary business messages keep using {@link #send(AbstractMQ)}. Reliability-sensitive
     * bridges such as CDC use this method so an asynchronous producer failure can participate in
     * the source consumer retry.</p>
     */
    default void sendConfirmed(AbstractMQ abstractMQ) {
        send(abstractMQ);
    }

    /**
     * Sends a delayed message.
     *
     * @param delay delay seconds
     */
    void send(AbstractMQ abstractMQ, int delay);
}

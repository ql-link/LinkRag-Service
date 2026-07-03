package com.qingluo.link.components.mq;

import com.qingluo.link.components.mq.constant.MQSendType;

import java.util.Collections;
import java.util.Map;

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

    /**
     * Optional transport headers sent alongside the message body.
     *
     * <p>Headers are not part of the JSON payload contract. They are used for cross-service
     * metadata such as {@code X-Trace-Id}.</p>
     */
    default Map<String, String> getHeaders() {
        return Collections.emptyMap();
    }
}

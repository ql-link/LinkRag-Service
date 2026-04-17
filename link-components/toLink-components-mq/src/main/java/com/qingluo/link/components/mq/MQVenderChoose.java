package com.qingluo.link.components.mq;

/**
 * MQ vendor selection constants. The misspelling "vender" is kept for
 * compatibility with the QingLuoPay-style configuration convention.
 */
public final class MQVenderChoose {

    public static final String YML_VENDER_KEY = "qingluopay.mq.vender";
    public static final String YML_VENDOR_KEY = "qingluopay.mq.vendor";
    public static final String RABBIT_MQ = "rabbitMQ";
    public static final String KAFKA = "kafka";

    private MQVenderChoose() {
    }
}

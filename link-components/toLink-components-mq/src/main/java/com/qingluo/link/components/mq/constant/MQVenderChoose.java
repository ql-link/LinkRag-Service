package com.qingluo.link.components.mq.constant;

/**
 * MQ vendor selection constants. The misspelling "vender" is kept for
 * backward compatibility with existing configuration.
 */
public final class MQVenderChoose {

    public static final String YML_VENDER_KEY = "tolink.mq.vender";
    public static final String RABBIT_MQ = "rabbitMQ";
    public static final String KAFKA = "kafka";

    private MQVenderChoose() {
    }
}

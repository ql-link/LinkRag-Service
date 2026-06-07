package com.qingluo.link.service.mq.kafka;

import com.qingluo.link.components.mq.MQMsgReceiver;
import com.qingluo.link.components.mq.constant.MQVenderChoose;
import com.qingluo.link.core.trace.TraceContext;
import com.qingluo.link.service.mq.CacheCompensationMQ;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 缓存补偿 Kafka 接收器。
 *
 * <p>负责监听 `tolink.cache.evict` 主题，把原始消息反序列化后交给业务接收器，
 * 自身不承担缓存路由和删除细节。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.KAFKA)
@ConditionalOnBean(CacheCompensationMQ.MQReceiver.class)
@Slf4j
public class CacheCompensationKafkaReceiver implements MQMsgReceiver {

    private final CacheCompensationMQ.MQReceiver receiver;

    @Override
    @KafkaListener(
            topics = CacheCompensationMQ.MQ_NAME,
            groupId = "${tolink.cache-consistency.consumer.group-id:tolink-cache-evict}"
    )
    public void receive(String msg) {
        TraceContext.startNew();
        try {
            log.info("收到缓存补偿 MQ 消息");
            receiver.receive(CacheCompensationMQ.parseMsg(msg));
        } finally {
            TraceContext.clear();
        }
    }
}

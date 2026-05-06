package com.qingluo.link.service.mq;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 缓存补偿业务接收器。
 *
 * <p>负责把 MQ 消息转换成统一删缓存调用，保持 Kafka 适配层足够薄，
 * 让补偿逻辑集中沉淀在缓存一致性组件里。</p>
 */
@Component
@RequiredArgsConstructor
public class CacheCompensationMQReceiver implements CacheCompensationMQ.MQReceiver {

    private final CacheConsistencyService cacheConsistencyService;

    @Override
    public void receive(CacheCompensationMQ.MsgPayload payload) {
        cacheConsistencyService.evictCompensation(payload.parseTarget(), payload.getRouteId());
    }
}

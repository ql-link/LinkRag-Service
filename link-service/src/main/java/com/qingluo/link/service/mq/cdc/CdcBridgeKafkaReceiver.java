package com.qingluo.link.service.mq.cdc;

import com.qingluo.link.components.mq.MQMsgReceiver;
import com.qingluo.link.core.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CDC 桥接 Kafka 接收器：消费 Canal 原始变更 topic，交 {@link CdcBridgeService} 翻译并投递补偿消息。
 *
 * <p>条件装配：vender=kafka 且 cdc.enabled=true 才装载——CDC 未部署的本地/测试环境零报错启动。
 * 两个属性条件用单个 {@link ConditionalOnExpression} 合并（同类型 @ConditionalOnProperty 不可叠加）。
 * 失败分类（退避重试 / 不可重试跳过）由专用容器工厂承接，见 CdcBridgeKafkaConfig。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${tolink.mq.vender:}'.equals('kafka') "
                + "and '${tolink.cache-consistency.cdc.enabled:false}'.equals('true')")
public class CdcBridgeKafkaReceiver implements MQMsgReceiver {

    private final CdcBridgeService cdcBridgeService;

    @Override
    @KafkaListener(
            topics = "${tolink.cache-consistency.cdc.source-topic}",
            groupId = "${tolink.cache-consistency.cdc.group-id:tolink-cdc-bridge}",
            containerFactory = "cdcBridgeKafkaListenerContainerFactory")
    public void receive(String msg) {
        TraceContext.startNew();
        try {
            cdcBridgeService.handle(msg);
        } finally {
            TraceContext.clear();
        }
    }
}

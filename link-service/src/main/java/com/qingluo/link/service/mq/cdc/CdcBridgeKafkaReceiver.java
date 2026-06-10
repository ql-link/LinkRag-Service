package com.qingluo.link.service.mq.cdc;

import com.qingluo.link.components.mq.MQMsgReceiver;
import com.qingluo.link.core.trace.TraceContext;
import com.qingluo.link.service.mq.config.CdcBridgeKafkaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CDC 桥接 Kafka 接收器：消费 Canal 原始变更 topic，交 {@link CdcBridgeService} 翻译并投递补偿消息。
 *
 * <p>条件装配复用 {@link CdcBridgeKafkaConfig#CDC_BRIDGE_CONDITION}：vender=kafka 且 cdc.enabled=true
 * 才装载——CDC 未部署的本地/测试环境零报错启动。与容器工厂 {@link CdcBridgeKafkaConfig} 共用同一条件，
 * 确保 CDC 开关一处生效、口径一致。失败分类（退避重试 / 不可重试跳过）见 {@link CdcBridgeKafkaConfig}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(CdcBridgeKafkaConfig.CDC_BRIDGE_CONDITION)
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

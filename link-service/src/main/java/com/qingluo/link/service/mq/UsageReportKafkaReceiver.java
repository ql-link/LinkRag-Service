package com.qingluo.link.service.mq;

import com.qingluo.link.components.mq.MQMsgReceiver;
import com.qingluo.link.components.mq.constant.MQVenderChoose;
import com.qingluo.link.components.mq.model.UsageReportMQ;
import com.qingluo.link.core.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 用量上报 Kafka 接收器：监听 {@code tolink.rag.usage_report}，反序列化后转交业务消费者。
 *
 * <p>topic 由 KafkaMQTopologyScanner 扫描 {@link UsageReportMQ}（实现 AbstractMQ）自动注册创建，
 * 与 chat_turn 一致。本接收器仅做线程级日志 traceId 串联，不写入消息体。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.KAFKA)
@Slf4j
public class UsageReportKafkaReceiver implements MQMsgReceiver {

    private final UsageReportMQ.MQReceiver receiver;

    @Override
    @KafkaListener(
            topics = UsageReportMQ.MQ_NAME,
            groupId = "${tolink.mq.usage-report.group-id:tolink-java-usage-report-worker}"
    )
    public void receive(String msg) {
        TraceContext.startNew();
        try {
            log.info("Receive usage report MQ message");
            receiver.receive(UsageReportMQ.parseMsg(msg));
        } finally {
            TraceContext.clear();
        }
    }
}

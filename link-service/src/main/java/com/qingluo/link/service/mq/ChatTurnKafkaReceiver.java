package com.qingluo.link.service.mq;

import com.qingluo.link.components.mq.MQMsgReceiver;
import com.qingluo.link.components.mq.constant.MQVenderChoose;
import com.qingluo.link.components.mq.model.ChatTurnMQ;
import com.qingluo.link.observability.trace.TraceContext;
import com.qingluo.link.service.mq.kafka.KafkaTraceHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 对话轮次 Kafka 接收器：监听 {@code tolink.rag.chat_turn}，反序列化后转交业务消费者。
 *
 * <p>topic 由 KafkaMQTopologyScanner 扫描 {@link ChatTurnMQ}（实现 AbstractMQ）自动注册创建，
 * 与 parse_result 一致。本接收器从 Kafka header 恢复 trace_id，仅做线程级日志串联，不写入消息体。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.KAFKA)
@Slf4j
public class ChatTurnKafkaReceiver implements MQMsgReceiver {

    private final ChatTurnMQ.MQReceiver receiver;

    @Override
    public void receive(String msg) {
        receiveWithTrace(msg, null);
    }

    @KafkaListener(
            topics = ChatTurnMQ.MQ_NAME,
            groupId = "${tolink.mq.chat-turn.group-id:tolink-java-chat-turn-worker}"
    )
    public void receive(ConsumerRecord<String, String> record) {
        receiveWithTrace(record.value(), KafkaTraceHeaders.traceId(record.headers()));
    }

    private void receiveWithTrace(String msg, String inboundTraceId) {
        TraceContext.start(inboundTraceId);
        try {
            log.info("Receive chat turn MQ message");
            receiver.receive(ChatTurnMQ.parseMsg(msg));
        } finally {
            TraceContext.clear();
        }
    }
}

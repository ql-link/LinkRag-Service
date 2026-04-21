package com.qingluo.link.service.mq.kafka;

import com.qingluo.link.components.mq.MQMsgReceiver;
import com.qingluo.link.components.mq.MQVenderChoose;
import com.qingluo.link.service.mq.KnowledgeParseResultMQ;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = MQVenderChoose.YML_VENDER_KEY, havingValue = MQVenderChoose.KAFKA)
@ConditionalOnBean(KnowledgeParseResultMQ.MQReceiver.class)
@Slf4j
public class KnowledgeParseResultKafkaReceiver implements MQMsgReceiver {

    private final KnowledgeParseResultMQ.MQReceiver receiver;

    @Override
    @KafkaListener(
        topics = KnowledgeParseResultMQ.MQ_NAME,
        groupId = "${tolink.mq.parse-result.group-id:tolink-java-parse-result-worker}"
    )
    public void receive(String msg) {
        log.info("Receive parse result MQ message");
        receiver.receive(KnowledgeParseResultMQ.parseMsg(msg));
    }
}

package com.qingluo.link.service.mq.kafka;

import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import com.qingluo.link.service.mq.KnowledgeParseResultKafkaReceiver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeParseResultKafkaReceiverTest {

    @Mock
    private KnowledgeParseResultMQ.MQReceiver receiver;

    @InjectMocks
    private KnowledgeParseResultKafkaReceiver kafkaReceiver;

    @Test
    @DisplayName("Should_ParseAndDispatchParseResultPayload_When_ReceiveKafkaMessage")
    void Should_ParseAndDispatchParseResultPayload_When_ReceiveKafkaMessage() {
        kafkaReceiver.receive("""
            {"mq_type":"parse_result","mq_name":"tolink.rag.parse_result","payload":{"task_id":"task-1","document_id":"101","success":true,"status":"success","parsed_bucket_name":"rag-parsed","parsed_object_key":"parsed/key","parsed_file_url":"http://rag/key","failure_reason":"","time_cost_ms":123}}
            """);

        verify(receiver).receive(new KnowledgeParseResultMQ.MsgPayload(
            "task-1",
            "101",
            true,
            "success",
            "rag-parsed",
            "parsed/key",
            "http://rag/key",
            "",
            123L
        ));
    }
}

package com.qingluo.link.service.mq.kafka;

import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.model.DocumentParseResultMQ;
import com.qingluo.link.service.mq.DocumentParseResultKafkaReceiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentParseResultKafkaReceiverTest {

    @Mock
    private DocumentParseResultMQ.MQReceiver receiver;

    @InjectMocks
    private DocumentParseResultKafkaReceiver kafkaReceiver;

    @Test
    void Should_ParseFlatPythonContract_When_ReceiveKafkaMessage() {
        kafkaReceiver.receive("""
            {"task_id":"task-1","original_file_id":101,"document_parsed_log_id":201,"dataset_id":301,"user_id":401,"task_status":"success","failure_reason":null,"parse_finished_at":"2026-05-27T10:00:08+08:00"}
            """);

        verify(receiver).receive(new DocumentParseResultMQ.MsgPayload(
            "task-1", 101L, 201L, 301L, 401L, "success", null, "2026-05-27T10:00:08+08:00"
        ));
    }
}

package com.qingluo.link.service.cache.replay;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CacheReplayFailureRecorder {

    private final CacheReplayEventService replayEventService;

    public void record(String stage, ConsumerRecord<?, ?> record, String reason, Exception exception) {
        replayEventService.recordFailure(
            stage,
            record.topic(),
            record.partition(),
            record.offset(),
            record.value() == null ? null : String.valueOf(record.value()),
            reason,
            exception);
    }
}

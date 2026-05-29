package com.qingluo.link.service;

import com.qingluo.link.components.mq.model.DocumentParseResultMQ;
import com.qingluo.link.model.dto.request.DocumentParseCallbackRequest;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface DocumentParseSseService {

    SseEmitter subscribe(Long userId, Long datasetId, List<Long> fileIds);

    void publishTaskEvent(String taskId, DocumentParseCallbackRequest request);

    void publishResultEvent(DocumentParseResultMQ.MsgPayload payload);
}

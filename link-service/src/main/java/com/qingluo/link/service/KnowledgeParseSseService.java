package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.KnowledgeParseCallbackRequest;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 文件解析 SSE 推送服务。
 */
public interface KnowledgeParseSseService {

    SseEmitter subscribe(Long userId, Long datasetId, List<Long> fileIds);

    void publishTaskEvent(String taskId, KnowledgeParseCallbackRequest request);

    void publishResultEvent(KnowledgeParseResultMQ.MsgPayload payload);
}

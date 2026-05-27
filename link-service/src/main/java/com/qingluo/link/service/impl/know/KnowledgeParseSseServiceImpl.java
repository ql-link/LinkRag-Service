package com.qingluo.link.service.impl.know;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParsedLogMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParsedLog;
import com.qingluo.link.model.dto.request.KnowledgeParseCallbackRequest;
import com.qingluo.link.model.dto.response.FileParseEventDTO;
import com.qingluo.link.service.KnowledgeParseSseService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 单实例内存 SSE 连接管理；断连后前端通过结果查询获取数据库终态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseSseServiceImpl implements KnowledgeParseSseService {

    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParsedLogMapper knowledgeParsedLogMapper;
    private final KnowledgeFileProperties properties;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByFileId = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(Long userId, Long datasetId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(400, "请选择要查看的文件", 400);
        }
        List<Long> ids = fileIds.stream().distinct().toList();
        long owned = knowledgeOriginalFileMapper.selectCount(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getUserId, userId)
            .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
            .in(KnowledgeOriginalFile::getId, ids));
        if (owned != ids.size()) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        ids.forEach(id -> emittersByFileId.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>()).add(emitter));
        Runnable cleanup = () -> ids.forEach(id -> removeEmitter(id, emitter));
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    @Override
    public void publishTaskEvent(String taskId, KnowledgeParseCallbackRequest request) {
        KnowledgeParsedLog logRecord = knowledgeParsedLogMapper.selectOne(new LambdaQueryWrapper<KnowledgeParsedLog>()
            .eq(KnowledgeParsedLog::getTaskId, taskId).last("LIMIT 1"));
        if (logRecord == null) {
            throw new BusinessException(404, "解析任务不存在", 404);
        }
        KnowledgeOriginalFile file = requireFile(logRecord.getDocumentOriginalFileId());
        FileParseEventDTO event = buildEvent(file, "processing", request.getProgress(), null);
        sendToFile(file.getId(), event);
    }

    @Override
    public void publishResultEvent(KnowledgeParseResultMQ.MsgPayload payload) {
        KnowledgeOriginalFile file = requireFile(payload.getOriginalFileId());
        FileParseEventDTO event = buildEvent(file, payload.getTaskStatus(), null, payload.getFailureReason());
        sendToFile(file.getId(), event);
    }

    private KnowledgeOriginalFile requireFile(Long fileId) {
        KnowledgeOriginalFile file = knowledgeOriginalFileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        return file;
    }

    private FileParseEventDTO buildEvent(KnowledgeOriginalFile file, String status, Integer progress, String reason) {
        FileParseEventDTO event = new FileParseEventDTO();
        event.setFileId(file.getId());
        event.setOriginalFilename(file.getOriginalFilename());
        event.setParseStatus(status);
        event.setProgress(progress);
        event.setFailureReason(reason);
        event.setFrontendStatus("success".equals(status) ? "parse_success"
            : "failed".equals(status) ? "parse_failed" : "parsing");
        return event;
    }

    private void sendToFile(Long fileId, FileParseEventDTO event) {
        List<SseEmitter> emitters = emittersByFileId.get(fileId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("parse").data(event));
            } catch (IOException | IllegalStateException e) {
                removeEmitter(fileId, emitter);
                log.debug("Remove closed parse SSE emitter, fileId={}", fileId, e);
            }
        }
    }

    private void removeEmitter(Long fileId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByFileId.get(fileId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }
}

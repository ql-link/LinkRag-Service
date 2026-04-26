package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseTaskMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseTask;
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
 * 基于内存连接表的 SSE 推送实现。
 *
 * <p>二期不解决多实例跨节点推送，所以连接只保存在当前 Java 实例内存里。
 * 如果浏览器断开或推送失败，会移除 emitter，前端可通过结果查询兜底。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseSseServiceImpl implements KnowledgeParseSseService {

    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParseTaskMapper knowledgeParseTaskMapper;
    private final KnowledgeFileProperties properties;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByFileId = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(Long userId, Long datasetId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException(400, "请选择要查看的文件", 400);
        }
        List<Long> distinctFileIds = fileIds.stream().distinct().toList();
        long ownedCount = knowledgeOriginalFileMapper.selectCount(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getUserId, userId)
            .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
            .in(KnowledgeOriginalFile::getId, distinctFileIds));
        if (ownedCount != distinctFileIds.size()) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }

        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        for (Long fileId : distinctFileIds) {
            emittersByFileId.computeIfAbsent(fileId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        }
        Runnable cleanup = () -> removeEmitter(distinctFileIds, emitter);
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
        KnowledgeParseTask task = knowledgeParseTaskMapper.selectOne(new LambdaQueryWrapper<KnowledgeParseTask>()
            .eq(KnowledgeParseTask::getTaskId, taskId));
        if (task == null) {
            throw new BusinessException(404, "解析任务不存在", 404);
        }
        KnowledgeOriginalFile file = knowledgeOriginalFileMapper.selectById(task.getDocumentOriginalFileId());
        if (file == null) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }

        FileParseEventDTO event = new FileParseEventDTO();
        event.setFileId(file.getId());
        event.setOriginalFilename(file.getOriginalFilename());
        event.setProgress(request.getProgress());
        event.setParseStatus(resolveParseStatus(request.getEventType(), task.getTaskStatus()));
        event.setFrontendStatus(frontendStatus(event.getParseStatus()));
        event.setFailureReason(request.getFailureReason());
        sendToFile(file.getId(), event);
    }

    private String resolveParseStatus(String eventType, String currentStatus) {
        if ("progress".equals(eventType)) {
            return KnowledgeParseTaskServiceImpl.TASK_PROCESSING;
        }
        if ("processing".equals(eventType) || "success".equals(eventType) || "failed".equals(eventType)) {
            return eventType;
        }
        return currentStatus;
    }

    private String frontendStatus(String parseStatus) {
        if (KnowledgeParseTaskServiceImpl.TASK_CREATED.equals(parseStatus)) {
            return "parse_waiting";
        }
        if (KnowledgeParseTaskServiceImpl.TASK_PROCESSING.equals(parseStatus)) {
            return "parsing";
        }
        if (KnowledgeParseTaskServiceImpl.TASK_SUCCESS.equals(parseStatus)) {
            return "parse_success";
        }
        if (KnowledgeParseTaskServiceImpl.TASK_FAILED.equals(parseStatus)) {
            return "parse_failed";
        }
        return "parse_waiting";
    }

    private void sendToFile(Long fileId, FileParseEventDTO event) {
        List<SseEmitter> emitters = emittersByFileId.get(fileId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("parse").data(event));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("Remove closed parse SSE emitter, fileId={}", fileId, e);
            }
        }
    }

    private void removeEmitter(List<Long> fileIds, SseEmitter emitter) {
        for (Long fileId : fileIds) {
            List<SseEmitter> emitters = emittersByFileId.get(fileId);
            if (emitters != null) {
                emitters.remove(emitter);
            }
        }
    }
}

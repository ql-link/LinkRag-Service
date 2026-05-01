package com.qingluo.link.service.impl.know;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseTaskMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseTask;
import com.qingluo.link.service.KnowledgeParseSseService;
import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseResultServiceImpl implements KnowledgeParseResultService {

    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParseTaskMapper knowledgeParseTaskMapper;
    private final KnowledgeParseSseService knowledgeParseSseService;

    @Override
    public void handleParseResult(KnowledgeParseResultMQ.MsgPayload payload) {
        KnowledgeParseTask task = knowledgeParseTaskMapper.selectById(payload.getDocumentParseLogId());
        if (task == null) {
            log.warn("Ignore parse result because task log is missing, taskId={}, parseLogId={}",
                payload.getTaskId(), payload.getDocumentParseLogId());
            throw new BusinessException(404, "解析任务不存在", 404);
        }
        if (!payload.getTaskId().equals(task.getTaskId())) {
            log.error("Reject parse result because task id mismatched, parseLogId={}, expectedTaskId={}, actualTaskId={}",
                payload.getDocumentParseLogId(), task.getTaskId(), payload.getTaskId());
            throw new BusinessException(400, "解析结果消息中的任务标识不匹配", 400);
        }

        KnowledgeOriginalFile record = knowledgeOriginalFileMapper.selectById(payload.getOriginalFileId());
        if (record == null) {
            log.warn("Ignore parse result because file record is missing, taskId={}, originalFileId={}",
                payload.getTaskId(), payload.getOriginalFileId());
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        if (!record.getId().equals(payload.getOriginalFileId())
            || !record.getDatasetId().equals(payload.getDatasetId())
            || !record.getUserId().equals(payload.getUserId())
            || !record.getId().equals(task.getDocumentOriginalFileId())) {
            log.error("Reject parse result because ownership mismatched, taskId={}, fileId={}, datasetId={}, userId={}",
                payload.getTaskId(), payload.getOriginalFileId(), payload.getDatasetId(), payload.getUserId());
            throw new BusinessException(400, "解析结果消息归属信息不匹配", 400);
        }

        if ("success".equals(payload.getTaskStatus())) {
            log.info("Handle parse result success, taskId={}, fileId={}, parseLogId={}, datasetId={}",
                payload.getTaskId(), payload.getOriginalFileId(), payload.getDocumentParseLogId(), record.getDatasetId());
        } else {
            log.warn("Handle parse result failure, taskId={}, fileId={}, parseLogId={}, datasetId={}, failureReason={}",
                payload.getTaskId(), payload.getOriginalFileId(), payload.getDocumentParseLogId(),
                record.getDatasetId(), payload.getFailureReason());
        }
        knowledgeParseSseService.publishResultEvent(payload);
    }
}

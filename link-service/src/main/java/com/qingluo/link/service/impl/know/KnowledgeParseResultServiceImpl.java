package com.qingluo.link.service.impl.know;

import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParseFileMapper;
import com.qingluo.link.mapper.KnowledgeParsedLogMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseFile;
import com.qingluo.link.model.dto.entity.KnowledgeParsedLog;
import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.service.KnowledgeParseSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 校验 Python 已持久化的终态结果，并将其转发给 SSE 订阅端。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseResultServiceImpl implements KnowledgeParseResultService {

    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParseFileMapper knowledgeParseFileMapper;
    private final KnowledgeParsedLogMapper knowledgeParsedLogMapper;
    private final KnowledgeParseSseService knowledgeParseSseService;

    @Override
    public void handleParseResult(KnowledgeParseResultMQ.MsgPayload payload) {
        KnowledgeParsedLog logRecord = knowledgeParsedLogMapper.selectById(payload.getDocumentParsedLogId());
        if (logRecord == null) {
            throw new BusinessException(404, "解析任务不存在", 404);
        }
        if (!payload.getTaskId().equals(logRecord.getTaskId())) {
            throw new BusinessException(400, "解析结果消息中的任务标识不匹配", 400);
        }
        if (!payload.getTaskStatus().equals(logRecord.getTaskStatus())) {
            throw new BusinessException(400, "解析结果消息状态与已持久化状态不匹配", 400);
        }
        KnowledgeParseFile parseFile = knowledgeParseFileMapper.selectById(logRecord.getDocumentParseFileId());
        KnowledgeOriginalFile originalFile = knowledgeOriginalFileMapper.selectById(payload.getOriginalFileId());
        if (parseFile == null || originalFile == null
            || !payload.getOriginalFileId().equals(logRecord.getDocumentOriginalFileId())
            || !payload.getOriginalFileId().equals(parseFile.getDocumentOriginalFileId())
            || !payload.getOriginalFileId().equals(originalFile.getId())
            || !payload.getDatasetId().equals(parseFile.getDatasetId())
            || !payload.getDatasetId().equals(originalFile.getDatasetId())
            || !payload.getUserId().equals(parseFile.getUserId())
            || !payload.getUserId().equals(originalFile.getUserId())) {
            throw new BusinessException(400, "解析结果消息归属信息不匹配", 400);
        }

        log.info("Forward parse terminal result, taskId={}, originalFileId={}, parsedLogId={}, status={}",
            payload.getTaskId(), payload.getOriginalFileId(), payload.getDocumentParsedLogId(),
            payload.getTaskStatus());
        knowledgeParseSseService.publishResultEvent(payload);
    }
}

package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.service.mq.KnowledgeParseResultMQ;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseResultServiceImpl implements KnowledgeParseResultService {

    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;

    @Override
    @Transactional
    public void handleParseResult(KnowledgeParseResultMQ.MsgPayload payload) {
        KnowledgeOriginalFile record = knowledgeOriginalFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getParseTaskId, payload.getTaskId()));
        if (record == null) {
            log.warn("Ignore parse result because task record is missing, taskId={}, documentId={}",
                payload.getTaskId(), payload.getDocumentId());
            return;
        }
        if (!record.getId().toString().equals(payload.getDocumentId())) {
            log.error("Reject parse result because document id mismatched, taskId={}, expectedDocumentId={}, actualDocumentId={}",
                payload.getTaskId(), record.getId(), payload.getDocumentId());
            throw new BusinessException(400, "解析结果消息中的文档标识不匹配", 400);
        }
        if (Boolean.TRUE.equals(payload.getSuccess())) {
            handleSuccess(record, payload);
            return;
        }
        handleFailure(record, payload);
    }

    private void handleSuccess(KnowledgeOriginalFile record, KnowledgeParseResultMQ.MsgPayload payload) {
        // 一期重构后 document_parsed_file 只保存解析业务记录，不再由 Java 写解析产物。
        // 旧 parse_result MQ 仅保留日志兼容，二期由 Python 写 document_parse_log。
        log.info("Handle parse result success, taskId={}, documentId={}, datasetId={}, parsedObjectKey={}",
            payload.getTaskId(), payload.getDocumentId(), record.getDatasetId(), payload.getParsedObjectKey());
    }

    private void handleFailure(KnowledgeOriginalFile record, KnowledgeParseResultMQ.MsgPayload payload) {
        log.warn("Handle parse result failure, taskId={}, documentId={}, datasetId={}, failureReason={}",
            payload.getTaskId(), payload.getDocumentId(), record.getDatasetId(), payload.getFailureReason());
    }
}

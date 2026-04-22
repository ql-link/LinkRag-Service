package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.service.mq.KnowledgeParseResultMQ;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseResultServiceImpl implements KnowledgeParseResultService {

    private static final String PARSE_STATUS_SUCCESS = "success";
    private static final String PARSE_STATUS_FAILED = "failed";

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
        if (PARSE_STATUS_SUCCESS.equals(record.getParseStatus())) {
            log.info("Ignore parse result because record already succeeded, taskId={}, documentId={}, currentStatus={}",
                payload.getTaskId(), payload.getDocumentId(), record.getParseStatus());
            return;
        }

        if (Boolean.TRUE.equals(payload.getSuccess())) {
            handleSuccess(record, payload);
            return;
        }
        handleFailure(record, payload);
    }

    private void handleSuccess(KnowledgeOriginalFile record, KnowledgeParseResultMQ.MsgPayload payload) {
        record.setParseStatus(PARSE_STATUS_SUCCESS);
        record.setIsParseSuccess(true);
        record.setParsedBucketName(payload.getParsedBucketName());
        record.setParsedObjectKey(payload.getParsedObjectKey());
        record.setParsedFileUrl(payload.getParsedFileUrl());
        record.setParsedAt(LocalDateTime.now());
        record.setParseFailureReason(null);
        updateRecord(record);
        log.info("Handle parse result success, taskId={}, documentId={}, datasetId={}, parsedObjectKey={}",
            payload.getTaskId(), payload.getDocumentId(), record.getDatasetId(), payload.getParsedObjectKey());
    }

    private void handleFailure(KnowledgeOriginalFile record, KnowledgeParseResultMQ.MsgPayload payload) {
        record.setParseStatus(PARSE_STATUS_FAILED);
        record.setIsParseSuccess(false);
        record.setParsedBucketName(null);
        record.setParsedObjectKey(null);
        record.setParsedFileUrl(null);
        record.setParsedAt(null);
        record.setParseFailureReason(normalizeFailureReason(payload.getFailureReason()));
        updateRecord(record);
        log.warn("Handle parse result failure, taskId={}, documentId={}, datasetId={}, failureReason={}",
            payload.getTaskId(), payload.getDocumentId(), record.getDatasetId(), record.getParseFailureReason());
    }

    private void updateRecord(KnowledgeOriginalFile record) {
        knowledgeOriginalFileMapper.updateById(record);
    }

    private String normalizeFailureReason(String value) {
        return StringUtils.hasText(value) ? value : "文件解析失败";
    }
}

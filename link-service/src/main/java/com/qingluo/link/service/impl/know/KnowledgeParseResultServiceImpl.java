package com.qingluo.link.service.impl.know;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.service.KnowledgeParseResultService;
import com.qingluo.link.components.mq.model.KnowledgeParseResultMQ;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 知识文件解析结果服务实现，负责消费解析回调并更新文件状态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeParseResultServiceImpl implements KnowledgeParseResultService {

    private static final String PARSE_STATUS_SUCCESS = "success";
    private static final String PARSE_STATUS_FAILED = "failed";

    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;

    @Override
    @Transactional
    /**
     * 根据解析结果消息更新原文件解析状态。
     */
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

    /**
     * 处理解析成功场景并回写产物信息。
     */
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

    /**
     * 处理解析失败场景并记录失败原因。
     */
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

    /**
     * 持久化更新后的文件记录。
     */
    private void updateRecord(KnowledgeOriginalFile record) {
        knowledgeOriginalFileMapper.updateById(record);
    }

    /**
     * 统一归一化解析失败原因。
     */
    private String normalizeFailureReason(String value) {
        return StringUtils.hasText(value) ? value : "文件解析失败";
    }
}

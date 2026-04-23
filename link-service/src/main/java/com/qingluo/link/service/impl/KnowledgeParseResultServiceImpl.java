package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.KnowledgeParsedFile;
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
    private final KnowledgeParsedFileMapper knowledgeParsedFileMapper;

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
        KnowledgeParsedFile existingParsedFile = getParsedFile(record.getId());
        if (existingParsedFile != null && PARSE_STATUS_SUCCESS.equals(existingParsedFile.getParseStatus())) {
            log.info("Ignore parse result because record already succeeded, taskId={}, documentId={}, currentStatus={}",
                payload.getTaskId(), payload.getDocumentId(), existingParsedFile.getParseStatus());
            return;
        }

        if (Boolean.TRUE.equals(payload.getSuccess())) {
            handleSuccess(record, payload, existingParsedFile);
            return;
        }
        handleFailure(record, payload, existingParsedFile);
    }

    private void handleSuccess(KnowledgeOriginalFile record, KnowledgeParseResultMQ.MsgPayload payload,
                               KnowledgeParsedFile parsedFile) {
        saveOrUpdateParsedFile(record, payload, true, parsedFile);
        log.info("Handle parse result success, taskId={}, documentId={}, datasetId={}, parsedObjectKey={}",
            payload.getTaskId(), payload.getDocumentId(), record.getDatasetId(), payload.getParsedObjectKey());
    }

    private void handleFailure(KnowledgeOriginalFile record, KnowledgeParseResultMQ.MsgPayload payload,
                               KnowledgeParsedFile parsedFile) {
        saveOrUpdateParsedFile(record, payload, false, parsedFile);
        log.warn("Handle parse result failure, taskId={}, documentId={}, datasetId={}, failureReason={}",
            payload.getTaskId(), payload.getDocumentId(), record.getDatasetId(), normalizeFailureReason(payload.getFailureReason()));
    }

    private String normalizeFailureReason(String value) {
        return StringUtils.hasText(value) ? value : "文件解析失败";
    }

    private void saveOrUpdateParsedFile(KnowledgeOriginalFile record, KnowledgeParseResultMQ.MsgPayload payload,
                                        boolean parseSuccess, KnowledgeParsedFile parsedFile) {
        if (parsedFile == null) {
            parsedFile = new KnowledgeParsedFile();
            parsedFile.setDocumentOriginalFileId(record.getId());
            parsedFile.setDatasetId(record.getDatasetId());
            parsedFile.setUserId(record.getUserId());
            parsedFile.setParseTaskId(record.getParseTaskId());
            parsedFile.setOriginalFilename(record.getOriginalFilename());
        }

        parsedFile.setParseStatus(parseSuccess ? PARSE_STATUS_SUCCESS : PARSE_STATUS_FAILED);
        parsedFile.setIsParseSuccess(parseSuccess);
        parsedFile.setParseResult(payload.getStatus());
        parsedFile.setLastResultAt(LocalDateTime.now());

        if (parseSuccess) {
            parsedFile.setParsedBucketName(payload.getParsedBucketName());
            parsedFile.setParsedObjectKey(payload.getParsedObjectKey());
            parsedFile.setParsedFileUrl(payload.getParsedFileUrl());
            parsedFile.setParsedFilename(extractFilename(payload.getParsedObjectKey()));
            parsedFile.setParsedStoragePath(buildStoragePath(payload.getParsedBucketName(), payload.getParsedObjectKey()));
            parsedFile.setFailureReason(null);
            parsedFile.setParsedAt(LocalDateTime.now());
        } else {
            parsedFile.setParsedBucketName(null);
            parsedFile.setParsedObjectKey(null);
            parsedFile.setParsedFileUrl(null);
            parsedFile.setParsedFilename(null);
            parsedFile.setParsedStoragePath(null);
            parsedFile.setFailureReason(normalizeFailureReason(payload.getFailureReason()));
            parsedFile.setParsedAt(null);
        }

        if (parsedFile.getId() == null) {
            knowledgeParsedFileMapper.insert(parsedFile);
            return;
        }
        knowledgeParsedFileMapper.updateById(parsedFile);
    }

    private KnowledgeParsedFile getParsedFile(Long originalFileId) {
        return knowledgeParsedFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeParsedFile>()
            .eq(KnowledgeParsedFile::getDocumentOriginalFileId, originalFileId));
    }

    private String extractFilename(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        int separatorIndex = objectKey.lastIndexOf('/');
        if (separatorIndex < 0) {
            return objectKey;
        }
        return objectKey.substring(separatorIndex + 1);
    }

    private String buildStoragePath(String bucketName, String objectKey) {
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(objectKey)) {
            return null;
        }
        return bucketName + "/" + objectKey;
    }
}

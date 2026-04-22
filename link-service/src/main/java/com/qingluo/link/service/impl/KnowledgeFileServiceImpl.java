package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.mq.constant.MQSendType;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.KnowledgeFileDownloadResource;
import com.qingluo.link.service.KnowledgeFileService;
import com.qingluo.link.service.KnowledgeFileRuntimeConfigService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.config.KnowledgeFileRuntimeConfig;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeFileServiceImpl implements KnowledgeFileService {

    private static final String UPLOADING = "uploading";
    private static final String UPLOAD_SUCCESS = "success";
    private static final String UPLOAD_FAILED = "failed";
    private static final String PARSE_NOTICE_PENDING = "pending";
    private static final String PARSE_NOTICE_SENT = "sent";
    private static final String PARSE_NOTICE_FAILED = "failed";
    private static final String PARSE_STATUS_NOT_STARTED = "not_started";
    private static final String PARSE_STATUS_PENDING = "pending";

    private final DatasetMapper datasetMapper;
    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final IOssService ossService;
    private final PrivateFileResolver privateFileResolver;
    private final ObjectProvider<MQSend> mqSendProvider;
    private final KnowledgeFileProperties properties;
    private final KnowledgeFileRuntimeConfigService knowledgeFileRuntimeConfigService;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public KnowledgeFileDTO upload(Long userId, Long datasetId, MultipartFile file, boolean parseImmediately) {
        assertOwnedDataset(userId, datasetId);
        validateFile(file);

        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String suffix = extractSuffix(originalFilename);
        assertNoDuplicateOriginalFilename(userId, datasetId, originalFilename);

        KnowledgeOriginalFile record = new KnowledgeOriginalFile();
        record.setDatasetId(datasetId);
        record.setUserId(userId);
        record.setOriginalFilename(originalFilename);
        record.setFileSuffix(suffix);
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        record.setBucketName(ossService.getBucketName(OssSavePlaceEnum.PRIVATE));
        record.setUploadStatus(UPLOADING);
        record.setIsUploadSuccess(false);
        record.setParseNoticeStatus(PARSE_NOTICE_PENDING);
        record.setParseTaskId(UUID.randomUUID().toString());
        record.setParseStatus(PARSE_STATUS_NOT_STARTED);
        record.setIsParseSuccess(false);
        record.setParseNoticeRetryCount(0);
        try {
            knowledgeOriginalFileMapper.insert(record);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(400, "当前数据集下已存在同名原文件，请先重命名后再上传", 400);
        }

        String objectKey = buildObjectKey(userId, datasetId, originalFilename);
        String uploadResult = ossService.upload2PreviewUrl(OssSavePlaceEnum.PRIVATE, file, objectKey);
        if (!StringUtils.hasText(uploadResult)) {
            knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
                .eq(KnowledgeOriginalFile::getId, record.getId())
                .set(KnowledgeOriginalFile::getUploadStatus, UPLOAD_FAILED)
                .set(KnowledgeOriginalFile::getIsUploadSuccess, false)
                .set(KnowledgeOriginalFile::getFailureReason, "文件上传失败，请稍后重试"));
            log.error("Upload knowledge file to oss failed, userId={}, datasetId={}, fileName={}, parseTaskId={}",
                userId, datasetId, originalFilename, record.getParseTaskId());
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        }

        String fileUrl = normalizeBaseUrl(properties.getInternalBaseUrl())
            + "/api/v1/internal/knowledge-files/" + record.getId() + "/content?taskId=" + record.getParseTaskId();
        knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, record.getId())
            .set(KnowledgeOriginalFile::getObjectKey, uploadResult)
            .set(KnowledgeOriginalFile::getFileUrl, fileUrl)
            .set(KnowledgeOriginalFile::getUploadStatus, UPLOAD_SUCCESS)
            .set(KnowledgeOriginalFile::getIsUploadSuccess, true));

        record.setObjectKey(uploadResult);
        record.setFileUrl(fileUrl);
        record.setUploadStatus(UPLOAD_SUCCESS);
        record.setIsUploadSuccess(true);
        if (parseImmediately) {
            return createParseTask(record);
        }
        return toDTO(record);
    }

    @Override
    public PageResult<KnowledgeFileDTO> list(Long userId, Long datasetId, String uploadStatus,
                                             String parseNoticeStatus, String parseStatus, int page, int pageSize) {
        assertOwnedDataset(userId, datasetId);
        PageHelper.startPage(page, pageSize);
        LambdaQueryWrapper<KnowledgeOriginalFile> wrapper = new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
            .eq(KnowledgeOriginalFile::getUserId, userId)
            .orderByDesc(KnowledgeOriginalFile::getCreatedAt)
            .orderByDesc(KnowledgeOriginalFile::getId);
        if (StringUtils.hasText(uploadStatus)) {
            wrapper.eq(KnowledgeOriginalFile::getUploadStatus, normalizeStatus(uploadStatus));
        }
        if (StringUtils.hasText(parseNoticeStatus)) {
            wrapper.eq(KnowledgeOriginalFile::getParseNoticeStatus, normalizeStatus(parseNoticeStatus));
        }
        if (StringUtils.hasText(parseStatus)) {
            wrapper.eq(KnowledgeOriginalFile::getParseStatus, normalizeStatus(parseStatus));
        }
        List<KnowledgeOriginalFile> records = knowledgeOriginalFileMapper.selectList(wrapper);
        PageInfo<KnowledgeOriginalFile> pageInfo = new PageInfo<>(records);
        return new PageResult<>(records.stream().map(this::toDTO).toList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public KnowledgeFileDTO detail(Long userId, Long fileId) {
        return toDTO(getOwnedFile(userId, fileId));
    }

    @Override
    @Transactional
    public KnowledgeFileDTO createParseTask(Long userId, Long fileId) {
        KnowledgeOriginalFile record = getOwnedFile(userId, fileId);
        if (!Boolean.TRUE.equals(record.getIsUploadSuccess()) || !StringUtils.hasText(record.getObjectKey())) {
            throw new BusinessException(400, "原文件未上传成功，不能创建解析任务", 400);
        }
        if (PARSE_STATUS_PENDING.equals(record.getParseStatus())) {
            return toDTO(record);
        }
        return createParseTask(record);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long fileId) {
        KnowledgeOriginalFile record = getOwnedFile(userId, fileId);
        if (StringUtils.hasText(record.getObjectKey())) {
            boolean deleted = ossService.deleteFile(OssSavePlaceEnum.PRIVATE, record.getObjectKey());
            if (!deleted) {
                log.error("Delete knowledge file oss object failed, userId={}, fileId={}, datasetId={}, objectKey={}",
                    userId, fileId, record.getDatasetId(), record.getObjectKey());
                throw new BusinessException(500, "删除原文件失败，请稍后重试", 500);
            }
            try {
                privateFileResolver.evictPrivateFile(record.getObjectKey());
            } catch (RuntimeException e) {
                log.warn("Evict private file cache failed after oss delete, userId={}, fileId={}, datasetId={}, objectKey={}",
                    userId, fileId, record.getDatasetId(), record.getObjectKey(), e);
            }
        }
        try {
            knowledgeOriginalFileMapper.deleteById(record.getId());
        } catch (RuntimeException e) {
            log.error("Delete knowledge file database record failed after oss delete, userId={}, fileId={}, datasetId={}, objectKey={}",
                userId, fileId, record.getDatasetId(), record.getObjectKey(), e);
            throw new BusinessException(500, "原文件对象已删除，但数据库记录删除失败，请尽快补偿处理", 500);
        }
    }

    @Override
    public KnowledgeFileDownloadResource openOriginalFile(Long fileId, String taskId) {
        KnowledgeOriginalFile record = knowledgeOriginalFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, fileId));
        if (record == null || !Boolean.TRUE.equals(record.getIsUploadSuccess())) {
            throw new BusinessException(404, "文件不存在", 404);
        }
        if (!record.getParseTaskId().equals(taskId)) {
            throw new BusinessException(403, "任务不匹配", 403);
        }
        if (!StringUtils.hasText(record.getObjectKey())) {
            throw new BusinessException(404, "文件不存在", 404);
        }
        File file = privateFileResolver.getPrivateFile(record.getObjectKey());
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException(404, "文件不存在", 404);
        }
        return new KnowledgeFileDownloadResource(file, record.getOriginalFilename(), record.getContentType());
    }

    private KnowledgeFileDTO createParseTask(KnowledgeOriginalFile record) {
        knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, record.getId())
            .set(KnowledgeOriginalFile::getParseStatus, PARSE_STATUS_PENDING));
        record.setParseStatus(PARSE_STATUS_PENDING);

        try {
            MQSend mqSend = mqSendProvider.getIfAvailable();
            if (mqSend == null) {
                throw new IllegalStateException("MQ sender is not configured");
            }
            mqSend.send(new KnowledgeParseTaskMQ(record));
            knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
                .eq(KnowledgeOriginalFile::getId, record.getId())
                .set(KnowledgeOriginalFile::getParseNoticeStatus, PARSE_NOTICE_SENT)
                .set(KnowledgeOriginalFile::getFailureReason, null));
            record.setParseNoticeStatus(PARSE_NOTICE_SENT);
            record.setFailureReason(null);
        } catch (Exception e) {
            knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
                .eq(KnowledgeOriginalFile::getId, record.getId())
                .set(KnowledgeOriginalFile::getParseNoticeStatus, PARSE_NOTICE_FAILED)
                .set(KnowledgeOriginalFile::getFailureReason, "文件已上传，解析任务投递失败"));
            record.setParseNoticeStatus(PARSE_NOTICE_FAILED);
            record.setFailureReason("文件已上传，解析任务投递失败");
        }
        return toDTO(record);
    }

    private void assertOwnedDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getId, datasetId)
            .eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
    }

    private KnowledgeOriginalFile getOwnedFile(Long userId, Long fileId) {
        KnowledgeOriginalFile record = knowledgeOriginalFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, fileId)
            .eq(KnowledgeOriginalFile::getUserId, userId));
        if (record == null) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        return record;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
        }
        KnowledgeFileRuntimeConfig runtimeConfig = knowledgeFileRuntimeConfigService.getCurrent();
        String suffix = extractSuffix(file.getOriginalFilename());
        if (!runtimeConfig.getAllowedSuffixes().contains(suffix)) {
            throw new BusinessException(400, "当前文件格式暂不支持", 400);
        }
        if (file.getSize() > runtimeConfig.getMaxSizeBytes()) {
            throw new BusinessException(400, "文件大小超过限制", 400);
        }
    }

    private String extractSuffix(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new BusinessException(400, "当前文件格式暂不支持", 400);
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void assertNoDuplicateOriginalFilename(Long userId, Long datasetId, String originalFilename) {
        Long count = knowledgeOriginalFileMapper.selectCount(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getUserId, userId)
            .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
            .eq(KnowledgeOriginalFile::getOriginalFilename, originalFilename));
        if (count != null && count > 0) {
            throw new BusinessException(400, "当前数据集下已存在同名原文件，请先重命名后再上传", 400);
        }
    }

    private String buildObjectKey(Long userId, Long datasetId, String originalFilename) {
        LocalDate now = LocalDate.now();
        return "%d/%d/%04d/%02d/%02d/%s".formatted(
            userId, datasetId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), originalFilename);
    }

    private String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
        }
        String normalized = originalFilename.replace("\\", "/");
        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(separatorIndex + 1);
        }
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
        }
        if (!normalized.matches("[\\p{L}\\p{N} ._\\-]+")) {
            throw new BusinessException(400, "文件名包含非法字符", 400);
        }
        return normalized;
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "http://localhost:8080";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeStatus(String status) {
        return status.toLowerCase(Locale.ROOT)
            .replace("upload_", "")
            .replace("parse_notice_", "");
    }

    private KnowledgeFileDTO toDTO(KnowledgeOriginalFile record) {
        KnowledgeFileDTO dto = new KnowledgeFileDTO();
        dto.setId(record.getId());
        dto.setDatasetId(record.getDatasetId());
        dto.setOriginalFilename(record.getOriginalFilename());
        dto.setFileSuffix(record.getFileSuffix());
        dto.setFileSize(record.getFileSize());
        dto.setUploadStatus(toUploadStatus(record.getUploadStatus()));
        dto.setIsUploadSuccess(Boolean.TRUE.equals(record.getIsUploadSuccess()));
        dto.setParseNoticeStatus(toParseNoticeStatus(record.getParseNoticeStatus()));
        dto.setParseTaskId(record.getParseTaskId());
        dto.setParseStatus(toParseStatus(record.getParseStatus()));
        dto.setIsParseSuccess(Boolean.TRUE.equals(record.getIsParseSuccess()));
        dto.setFailureReason(record.getFailureReason());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        return dto;
    }

    private String toUploadStatus(String status) {
        return switch (status) {
            case UPLOAD_SUCCESS -> "UPLOAD_SUCCESS";
            case UPLOAD_FAILED -> "UPLOAD_FAILED";
            default -> "UPLOADING";
        };
    }

    private String toParseNoticeStatus(String status) {
        return switch (status) {
            case "sent" -> "PARSE_NOTICE_SENT";
            case "failed" -> "PARSE_NOTICE_FAILED";
            default -> "PARSE_NOTICE_PENDING";
        };
    }

    private String toParseStatus(String status) {
        return switch (status) {
            case "pending" -> "PENDING";
            case "processing" -> "PROCESSING";
            case "success" -> "SUCCESS";
            case "failed" -> "FAILED";
            default -> "NOT_STARTED";
        };
    }

    private static class KnowledgeParseTaskMQ implements AbstractMQ {

        private static final String MQ_NAME = "tolink.rag.parse_task";

        private final KnowledgeOriginalFile record;

        private KnowledgeParseTaskMQ() {
            this.record = new KnowledgeOriginalFile();
        }

        private KnowledgeParseTaskMQ(KnowledgeOriginalFile record) {
            this.record = record;
        }

        @Override
        public String getMQName() {
            return MQ_NAME;
        }

        @Override
        public MQSendType getMQType() {
            return MQSendType.QUEUE;
        }

        @Override
        public String getMessage() {
            return """
                {"mq_type":"parse_task","mq_name":"tolink.rag.parse_task","payload":{"task_id":"%s","document_id":"%s","file_url":"%s","file_type":"%s"}}
                """.formatted(
                    escape(record.getParseTaskId()),
                    record.getId(),
                    escape(record.getFileUrl()),
                    escape(record.getFileSuffix())).trim();
        }

        private String escape(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}

package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.KnowledgeOriginalFileMapper;
import com.qingluo.link.mapper.KnowledgeParsedFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParsedFile;
import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.KnowledgeFileDownloadResource;
import com.qingluo.link.service.KnowledgeFileRuntimeConfigService;
import com.qingluo.link.service.KnowledgeFileService;
import com.qingluo.link.service.KnowledgeParseTaskService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.config.KnowledgeFileRuntimeConfig;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库原文件上传服务实现。
 *
 * <p>本类是一期文件上传模块的核心编排点，负责把一次前端上传拆成几个确定步骤：
 * 校验数据集归属、基于唯一键处理幂等、写入 uploading 状态、通过线程池上传 MinIO、
 * 根据上传结果写 success/failed，并在上传成功后初始化解析文件业务记录。
 * 若前端选择 `parseImmediately=true`，则在上传事务提交后继续触发二期解析提交。
 *
 * <p>设计上刻意不在这里写解析状态：
 * 原文件表只描述“原文件是否已经成功进入对象存储”，解析任务和解析结果由二期独立表维护。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeFileServiceImpl implements KnowledgeFileService {

    /**
     * 原文件表只处理上传事实，不承载解析任务状态。
     * 二期虽然支持上传后自动解析，但解析任务和解析结果仍由独立表维护。
     */
    private static final String RAW_BUCKET = "rag-raw";
    private static final String UPLOADING = "uploading";
    private static final String UPLOAD_SUCCESS = "success";
    private static final String UPLOAD_FAILED = "failed";
    private static final String FAILURE_OSS_UPLOAD_FAILED = "OSS_UPLOAD_FAILED";
    private static final String FAILURE_UPLOAD_TIMEOUT = "UPLOAD_TIMEOUT";
    private static final String FAILURE_PARSED_FILE_INIT_FAILED = "PARSED_FILE_INIT_FAILED";
    private static final String FAILURE_UNKNOWN_UPLOAD_FAILED = "UNKNOWN_UPLOAD_FAILED";
    private static final long UPLOAD_TIMEOUT_MINUTES = 1L;
    private static final long OSS_UPLOAD_WAIT_SECONDS = 30L;

    private final DatasetMapper datasetMapper;
    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParsedFileMapper knowledgeParsedFileMapper;
    private final IOssService ossService;
    private final PrivateFileResolver privateFileResolver;
    private final KnowledgeFileProperties properties;
    private final KnowledgeFileRuntimeConfigService knowledgeFileRuntimeConfigService;
    private final KnowledgeParseTaskService knowledgeParseTaskService;
    @Qualifier("knowledgeFileUploadExecutor")
    private final Executor knowledgeFileUploadExecutor;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public KnowledgeFileDTO upload(Long userId, Long datasetId, MultipartFile file, boolean parseImmediately) {
        // 上传与解析是两个事务边界：
        // 上传成功必须先稳定落库；若选择自动解析，则在提交后进入单独的解析提交流程。
        assertOwnedDataset(userId, datasetId);
        validateFile(file);

        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String suffix = extractSuffix(originalFilename);
        KnowledgeOriginalFile record = findByUniqueKey(userId, datasetId, originalFilename, suffix);
        // 唯一键命中且已成功，说明同一用户在同一数据集下已经有同名同后缀原文件，必须拒绝重复上传。
        if (record != null && UPLOAD_SUCCESS.equals(record.getUploadStatus())) {
            throw new BusinessException(400, "当前数据集下已存在同名同后缀原文件", 400);
        }
        // uploading 未超时代表可能仍有线程正在写 OSS，直接重入会破坏 object_key 的最终状态。
        if (record != null && UPLOADING.equals(record.getUploadStatus()) && !isUploadTimedOut(record)) {
            throw new BusinessException(409, "文件正在上传中，请稍后重试", 409);
        }

        record = prepareUploadingRecord(userId, datasetId, file, originalFilename, suffix, record);
        // 失败重试必须复用历史 object_key，这样即使 MinIO 中存在残留对象，也会被同 key 覆盖。
        String objectKey = StringUtils.hasText(record.getObjectKey())
            ? record.getObjectKey()
            : buildObjectKey(userId, datasetId, record.getId(), originalFilename);
        // 在真正写 OSS 前先落 uploading，保证请求中断或进程异常时能被定时补偿扫描到。
        markUploading(record, file, objectKey);

        try {
            String uploadResult = uploadObjectWithTimeout(file, objectKey);
            if (!StringUtils.hasText(uploadResult)) {
                throw new IllegalStateException("OSS returned blank object key");
            }
        } catch (UploadTimeoutException e) {
            markFailed(record.getId(), FAILURE_UPLOAD_TIMEOUT);
            log.error("Upload original file to OSS timeout, userId={}, datasetId={}, fileId={}, objectKey={}",
                userId, datasetId, record.getId(), objectKey, e);
            throw new BusinessException(500, "文件上传超时，请重新上传", 500);
        } catch (RuntimeException e) {
            // OSS 失败后保留原文件记录，后续同一唯一键重试时复用 object_key 并覆盖对象。
            markFailed(record.getId(), FAILURE_OSS_UPLOAD_FAILED);
            log.error("Upload original file to OSS failed, userId={}, datasetId={}, fileId={}, objectKey={}",
                userId, datasetId, record.getId(), objectKey, e);
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        }

        String fileUrl = normalizeBaseUrl(properties.getInternalBaseUrl())
            + "/api/v1/internal/files/" + record.getId() + "/content";
        try {
            markSuccess(record.getId(), objectKey, fileUrl);
        } catch (RuntimeException e) {
            safeMarkFailed(record.getId(), FAILURE_UNKNOWN_UPLOAD_FAILED);
            log.error("Mark original file upload success failed, userId={}, datasetId={}, fileId={}, objectKey={}",
                userId, datasetId, record.getId(), objectKey, e);
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        }

        record.setObjectKey(objectKey);
        record.setFileUrl(fileUrl);
        record.setUploadStatus(UPLOAD_SUCCESS);
        record.setIsUploadSuccess(true);
        record.setFailureReason(null);
        try {
            initializeParsedFileIfAbsent(record);
        } catch (RuntimeException e) {
            // OSS 已成功但解析文件业务记录初始化失败时，一期仍按上传失败返回，等待用户手动重试。
            markFailed(record.getId(), FAILURE_PARSED_FILE_INIT_FAILED);
            log.error("Initialize parsed file failed after original upload success, userId={}, datasetId={}, fileId={}, objectKey={}",
                userId, datasetId, record.getId(), objectKey, e);
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        }
        if (parseImmediately) {
            submitAutoParseAfterUploadCommitted(userId, record);
        }
        return toDTO(record);
    }

    /**
     * 上传成功后的自动解析必须在原文件上传事务提交之后再触发。
     *
     * <p>这样可以保证：
     * 1. Python 或 Java 解析提交侧读取到的是已提交的原文件/解析聚合记录；
     * 2. 自动解析提交失败时只回滚解析提交事务，不影响本次已成功的上传事实。
     */
    private void submitAutoParseAfterUploadCommitted(Long userId, KnowledgeOriginalFile record) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    knowledgeParseTaskService.submitAutoParseAfterUpload(userId, record);
                }
            });
            return;
        }
        knowledgeParseTaskService.submitAutoParseAfterUpload(userId, record);
    }

    private String uploadObjectWithTimeout(MultipartFile file, String objectKey) {
        // OSS 写入放到专用线程池中，避免请求线程直接创建或持有底层上传线程；线程池也提供统一并发上限。
        CompletableFuture<String> uploadTask = CompletableFuture.supplyAsync(
            () -> ossService.upload2PreviewUrl(OssSavePlaceEnum.PRIVATE, file, objectKey),
            knowledgeFileUploadExecutor);
        try {
            return uploadTask.get(OSS_UPLOAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 即使底层 MinIO 稍后完成，DB 已进入 failed；后续重试会复用 object_key 并覆盖对象。
            uploadTask.cancel(true);
            throw new UploadTimeoutException(e);
        } catch (InterruptedException e) {
            // 保留中断标记，避免上层线程池或容器误判当前线程仍可继续执行后续任务。
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OSS upload interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("OSS upload failed", e.getCause());
        }
    }

    @Override
    public PageResult<KnowledgeFileDTO> list(Long userId, Long datasetId, String uploadStatus, int page, int pageSize) {
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
    public void delete(Long userId, Long fileId) {
        KnowledgeOriginalFile record = getOwnedFile(userId, fileId);
        if (StringUtils.hasText(record.getObjectKey())) {
            // 删除采用先 OSS 后 DB：避免 DB 先删成功但对象残留，导致用户不可见但存储仍占用。
            boolean deleted = ossService.deleteFile(OssSavePlaceEnum.PRIVATE, record.getObjectKey());
            if (!deleted) {
                log.error("Delete original file OSS object failed, userId={}, fileId={}, objectKey={}",
                    userId, fileId, record.getObjectKey());
                throw new BusinessException(500, "删除原文件失败，请稍后重试", 500);
            }
            try {
                // 私有文件解析器可能有本地缓存；对象已删后应清掉缓存，避免后续内部下载读到旧文件。
                privateFileResolver.evictPrivateFile(record.getObjectKey());
            } catch (RuntimeException e) {
                log.warn("Evict private file cache failed after OSS delete, userId={}, fileId={}, objectKey={}",
                    userId, fileId, record.getObjectKey(), e);
            }
        }
        try {
            knowledgeOriginalFileMapper.deleteById(record.getId());
        } catch (RuntimeException e) {
            // 这里不尝试重新上传对象，只暴露补偿错误；否则会引入删除链路的反向恢复复杂度。
            log.error("Delete original file database record failed after OSS delete, userId={}, fileId={}, objectKey={}",
                userId, fileId, record.getObjectKey(), e);
            throw new BusinessException(500, "原文件对象已删除，但数据库记录删除失败，请尽快补偿处理", 500);
        }
    }

    @Override
    public KnowledgeFileDownloadResource openOriginalFile(Long fileId) {
        KnowledgeOriginalFile record = knowledgeOriginalFileMapper.selectById(fileId);
        if (record == null || !Boolean.TRUE.equals(record.getIsUploadSuccess())
            || !StringUtils.hasText(record.getObjectKey())) {
            throw new BusinessException(404, "文件不存在", 404);
        }
        File file = privateFileResolver.getPrivateFile(record.getObjectKey());
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException(404, "文件不存在", 404);
        }
        return new KnowledgeFileDownloadResource(file, record.getOriginalFilename(), record.getContentType());
    }

    @Override
    @Transactional
    public int markTimeoutUploadsFailed() {
        // 一期不启用自动定时补偿；该方法只作为显式入口/测试工具。
        // 即使对象后来写入成功，也按失败展示，重试时覆盖同一个 object_key。
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(UPLOAD_TIMEOUT_MINUTES);
        return knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getUploadStatus, UPLOADING)
            .lt(KnowledgeOriginalFile::getUpdatedAt, expiredAt)
            .set(KnowledgeOriginalFile::getUploadStatus, UPLOAD_FAILED)
            .set(KnowledgeOriginalFile::getIsUploadSuccess, false)
            .set(KnowledgeOriginalFile::getFailureReason, FAILURE_UPLOAD_TIMEOUT));
    }

    private KnowledgeOriginalFile prepareUploadingRecord(Long userId, Long datasetId, MultipartFile file,
                                                        String originalFilename, String suffix,
                                                        KnowledgeOriginalFile existing) {
        if (existing != null) {
            // 只有 failed 或已超时 uploading 会走到这里；复用记录是为了保留唯一键和 object_key。
            return existing;
        }
        KnowledgeOriginalFile record = new KnowledgeOriginalFile();
        record.setDatasetId(datasetId);
        record.setUserId(userId);
        record.setOriginalFilename(originalFilename);
        record.setFileSuffix(suffix);
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        record.setBucketName(RAW_BUCKET);
        record.setUploadStatus(UPLOADING);
        record.setIsUploadSuccess(false);
        try {
            knowledgeOriginalFileMapper.insert(record);
            return record;
        } catch (DataIntegrityViolationException e) {
            // 唯一索引兜底幂等：并发插入时重新读取记录，再按状态走重试或拒绝。
            KnowledgeOriginalFile concurrent = findByUniqueKey(userId, datasetId, originalFilename, suffix);
            if (concurrent != null && UPLOAD_SUCCESS.equals(concurrent.getUploadStatus())) {
                throw new BusinessException(400, "当前数据集下已存在同名同后缀原文件", 400);
            }
            if (concurrent != null && UPLOADING.equals(concurrent.getUploadStatus()) && !isUploadTimedOut(concurrent)) {
                throw new BusinessException(409, "文件正在上传中，请稍后重试", 409);
            }
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
    }

    private void markUploading(KnowledgeOriginalFile record, MultipartFile file, String objectKey) {
        // 每次重试都刷新文件大小和 Content-Type，允许用户用同名文件重新覆盖一次失败上传。
        knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, record.getId())
            .set(KnowledgeOriginalFile::getFileSize, file.getSize())
            .set(KnowledgeOriginalFile::getContentType, file.getContentType())
            .set(KnowledgeOriginalFile::getBucketName, RAW_BUCKET)
            .set(KnowledgeOriginalFile::getObjectKey, objectKey)
            .set(KnowledgeOriginalFile::getFileUrl, null)
            .set(KnowledgeOriginalFile::getUploadStatus, UPLOADING)
            .set(KnowledgeOriginalFile::getIsUploadSuccess, false)
            .set(KnowledgeOriginalFile::getFailureReason, null));
        record.setFileSize(file.getSize());
        record.setContentType(file.getContentType());
        record.setBucketName(RAW_BUCKET);
        record.setObjectKey(objectKey);
        record.setFileUrl(null);
        record.setUploadStatus(UPLOADING);
        record.setIsUploadSuccess(false);
        record.setFailureReason(null);
    }

    private void markSuccess(Long fileId, String objectKey, String fileUrl) {
        // success 是原文件上传链路的唯一终态成功标识；一期不在这里写解析状态。
        knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, fileId)
            .set(KnowledgeOriginalFile::getBucketName, RAW_BUCKET)
            .set(KnowledgeOriginalFile::getObjectKey, objectKey)
            .set(KnowledgeOriginalFile::getFileUrl, fileUrl)
            .set(KnowledgeOriginalFile::getUploadStatus, UPLOAD_SUCCESS)
            .set(KnowledgeOriginalFile::getIsUploadSuccess, true)
            .set(KnowledgeOriginalFile::getFailureReason, null));
    }

    private void markFailed(Long fileId, String reason) {
        // failed 不是终止态，用户可以再次上传同名同后缀文件触发重试。
        knowledgeOriginalFileMapper.update(null, new LambdaUpdateWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, fileId)
            .set(KnowledgeOriginalFile::getUploadStatus, UPLOAD_FAILED)
            .set(KnowledgeOriginalFile::getIsUploadSuccess, false)
            .set(KnowledgeOriginalFile::getFailureReason, reason));
    }

    private void safeMarkFailed(Long fileId, String reason) {
        try {
            markFailed(fileId, reason);
        } catch (RuntimeException e) {
            log.error("Mark original file failed status failed, fileId={}, failureReason={}", fileId, reason, e);
        }
    }

    private KnowledgeParsedFile initializeParsedFileIfAbsent(KnowledgeOriginalFile record) {
        KnowledgeParsedFile existing = knowledgeParsedFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeParsedFile>()
            .eq(KnowledgeParsedFile::getDocumentOriginalFileId, record.getId()));
        if (existing != null) {
            return existing;
        }
        KnowledgeParsedFile parsedFile = new KnowledgeParsedFile();
        parsedFile.setDocumentOriginalFileId(record.getId());
        parsedFile.setDatasetId(record.getDatasetId());
        parsedFile.setUserId(record.getUserId());
        parsedFile.setOriginalFilename(record.getOriginalFilename());
        parsedFile.setParseCount(0);
        try {
            knowledgeParsedFileMapper.insert(parsedFile);
            return parsedFile;
        } catch (DataIntegrityViolationException e) {
            KnowledgeParsedFile concurrent = knowledgeParsedFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeParsedFile>()
                .eq(KnowledgeParsedFile::getDocumentOriginalFileId, record.getId()));
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
    }

    private KnowledgeOriginalFile findByUniqueKey(Long userId, Long datasetId, String originalFilename, String suffix) {
        return knowledgeOriginalFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
            .eq(KnowledgeOriginalFile::getUserId, userId)
            .eq(KnowledgeOriginalFile::getOriginalFilename, originalFilename)
            .eq(KnowledgeOriginalFile::getFileSuffix, suffix));
    }

    private boolean isUploadTimedOut(KnowledgeOriginalFile record) {
        LocalDateTime updatedAt = record.getUpdatedAt();
        return updatedAt != null && updatedAt.isBefore(LocalDateTime.now().minusMinutes(UPLOAD_TIMEOUT_MINUTES));
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

    private String buildObjectKey(Long userId, Long datasetId, Long fileId, String originalFilename) {
        LocalDate now = LocalDate.now();
        // user-/dataset- 前缀避免纯数字路径含义不清，fileId 用于同名文件失败重试时稳定定位对象。
        return "original/user-%d/dataset-%d/%04d/%02d/%02d/%d/%s".formatted(
            userId, datasetId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), fileId, originalFilename);
    }

    private String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
        }
        String normalized = originalFilename.replace("\\", "/");
        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex >= 0) {
            // 不做业务层文件名规范化，但必须剥离浏览器可能携带的本地路径，避免对象 key 越界。
            normalized = normalized.substring(separatorIndex + 1);
        }
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
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
        return status.toLowerCase(Locale.ROOT).replace("upload_", "");
    }

    private KnowledgeFileDTO toDTO(KnowledgeOriginalFile record) {
        // DTO 只暴露原文件上传事实；解析结果和解析任务状态二期由独立表与接口返回。
        KnowledgeFileDTO dto = new KnowledgeFileDTO();
        dto.setId(record.getId());
        dto.setDatasetId(record.getDatasetId());
        dto.setOriginalFilename(record.getOriginalFilename());
        dto.setFileSuffix(record.getFileSuffix());
        dto.setFileSize(record.getFileSize());
        dto.setUploadStatus(record.getUploadStatus());
        dto.setIsUploadSuccess(Boolean.TRUE.equals(record.getIsUploadSuccess()));
        dto.setFailureReason(record.getFailureReason());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        return dto;
    }

    private static class UploadTimeoutException extends RuntimeException {

        private UploadTimeoutException(Throwable cause) {
            super("OSS upload timeout", cause);
        }
    }
}

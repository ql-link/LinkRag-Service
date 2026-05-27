package com.qingluo.link.service.impl.know;

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
import com.qingluo.link.mapper.KnowledgeParseFileMapper;
import com.qingluo.link.mapper.KnowledgeParsedLogMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.KnowledgeOriginalFile;
import com.qingluo.link.model.dto.entity.KnowledgeParseFile;
import com.qingluo.link.model.dto.entity.KnowledgeParsedLog;
import com.qingluo.link.model.dto.response.KnowledgeFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.KnowledgeFileDownloadResource;
import com.qingluo.link.service.KnowledgeFileService;
import com.qingluo.link.service.KnowledgeFileRuntimeConfigService;
import com.qingluo.link.service.KnowledgeParseTaskService;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.config.KnowledgeFileRuntimeConfig;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识文件服务实现，负责原文件上传、查询、删除和解析任务投递。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeFileServiceImpl implements KnowledgeFileService {

    private static final String UPLOADING = "uploading";
    private static final String UPLOAD_SUCCESS = "success";
    private static final String UPLOAD_FAILED = "failed";
    private final DatasetMapper datasetMapper;
    private final KnowledgeOriginalFileMapper knowledgeOriginalFileMapper;
    private final KnowledgeParseFileMapper knowledgeParseFileMapper;
    private final KnowledgeParsedLogMapper knowledgeParsedLogMapper;
    private final IOssService ossService;
    private final PrivateFileResolver privateFileResolver;
    private final KnowledgeFileProperties properties;
    private final KnowledgeFileRuntimeConfigService knowledgeFileRuntimeConfigService;
    private final KnowledgeParseTaskService knowledgeParseTaskService;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    /**
     * 上传原始知识文件，并在需要时立即创建解析任务。
     */
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
            log.error("Upload knowledge file to oss failed, userId={}, datasetId={}, fileName={}",
                userId, datasetId, originalFilename);
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        }

        String fileUrl = normalizeBaseUrl(properties.getInternalBaseUrl())
            + "/api/v1/internal/files/" + record.getId() + "/content";
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
        record.setFailureReason(null);
        initializeParseFileIfAbsent(record);
        if (parseImmediately) {
            submitAutoParseAfterCommit(userId, record);
        }
        return toDTO(record);
    }

    @Override
    /**
     * 分页查询知识文件列表，并支持按状态筛选。
     */
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
    /**
     * 查询知识文件详情。
     */
    public KnowledgeFileDTO detail(Long userId, Long fileId) {
        return toDTO(getOwnedFile(userId, fileId));
    }

    @Override
    @Transactional
    /**
     * 删除知识文件及其 OSS 对象。
     */
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
            deleteParseRecords(record.getId());
            knowledgeOriginalFileMapper.deleteById(record.getId());
        } catch (RuntimeException e) {
            log.error("Delete knowledge file database record failed after oss delete, userId={}, fileId={}, datasetId={}, objectKey={}",
                userId, fileId, record.getDatasetId(), record.getObjectKey(), e);
            throw new BusinessException(500, "原文件对象已删除，但数据库记录删除失败，请尽快补偿处理", 500);
        }
    }

    @Override
    /**
     * 按文件标识和解析任务标识打开原始文件。
     */
    public KnowledgeFileDownloadResource openOriginalFile(Long fileId) {
        KnowledgeOriginalFile record = knowledgeOriginalFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, fileId));
        if (record == null || !Boolean.TRUE.equals(record.getIsUploadSuccess())) {
            throw new BusinessException(404, "文件不存在", 404);
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

    private void initializeParseFileIfAbsent(KnowledgeOriginalFile file) {
        KnowledgeParseFile existing = knowledgeParseFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeParseFile>()
            .eq(KnowledgeParseFile::getDocumentOriginalFileId, file.getId()));
        if (existing != null) {
            return;
        }
        KnowledgeParseFile parseFile = new KnowledgeParseFile();
        parseFile.setDocumentOriginalFileId(file.getId());
        parseFile.setDatasetId(file.getDatasetId());
        parseFile.setUserId(file.getUserId());
        parseFile.setOriginalFilename(file.getOriginalFilename());
        parseFile.setParseCount(0);
        try {
            knowledgeParseFileMapper.insert(parseFile);
        } catch (DataIntegrityViolationException e) {
            if (knowledgeParseFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeParseFile>()
                .eq(KnowledgeParseFile::getDocumentOriginalFileId, file.getId())) == null) {
                throw e;
            }
        }
    }

    private void deleteParseRecords(Long originalFileId) {
        KnowledgeParseFile parseFile = knowledgeParseFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeParseFile>()
            .eq(KnowledgeParseFile::getDocumentOriginalFileId, originalFileId));
        if (parseFile != null) {
            knowledgeParsedLogMapper.delete(new LambdaQueryWrapper<KnowledgeParsedLog>()
                .eq(KnowledgeParsedLog::getDocumentParseFileId, parseFile.getId()));
            knowledgeParseFileMapper.deleteById(parseFile.getId());
            return;
        }
        knowledgeParsedLogMapper.delete(new LambdaQueryWrapper<KnowledgeParsedLog>()
            .eq(KnowledgeParsedLog::getDocumentOriginalFileId, originalFileId));
    }

    private void submitAutoParseAfterCommit(Long userId, KnowledgeOriginalFile file) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    knowledgeParseTaskService.submitAutoParseAfterUpload(userId, file);
                }
            });
            return;
        }
        knowledgeParseTaskService.submitAutoParseAfterUpload(userId, file);
    }

    /**
     * 校验数据集是否归属于当前用户。
     */
    private void assertOwnedDataset(Long userId, Long datasetId) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
            .eq(Dataset::getId, datasetId)
            .eq(Dataset::getUserId, userId));
        if (dataset == null) {
            throw new BusinessException(404, "数据集不存在或无权访问", 404);
        }
    }

    /**
     * 查询当前用户可访问的知识文件记录。
     */
    private KnowledgeOriginalFile getOwnedFile(Long userId, Long fileId) {
        KnowledgeOriginalFile record = knowledgeOriginalFileMapper.selectOne(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getId, fileId)
            .eq(KnowledgeOriginalFile::getUserId, userId));
        if (record == null) {
            throw new BusinessException(404, "文件不存在或无权访问", 404);
        }
        return record;
    }

    /**
     * 校验上传文件是否存在、格式合法且大小符合限制。
     */
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

    /**
     * 提取并标准化文件后缀。
     */
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

    /**
     * 校验同一数据集下是否已存在同名原文件。
     */
    private void assertNoDuplicateOriginalFilename(Long userId, Long datasetId, String originalFilename) {
        Long count = knowledgeOriginalFileMapper.selectCount(new LambdaQueryWrapper<KnowledgeOriginalFile>()
            .eq(KnowledgeOriginalFile::getUserId, userId)
            .eq(KnowledgeOriginalFile::getDatasetId, datasetId)
            .eq(KnowledgeOriginalFile::getOriginalFilename, originalFilename));
        if (count != null && count > 0) {
            throw new BusinessException(400, "当前数据集下已存在同名原文件，请先重命名后再上传", 400);
        }
    }

    /**
     * 按用户、数据集和日期生成对象存储路径。
     */
    private String buildObjectKey(Long userId, Long datasetId, String originalFilename) {
        LocalDate now = LocalDate.now();
        return "%d/%d/%04d/%02d/%02d/%s".formatted(
            userId, datasetId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), originalFilename);
    }

    /**
     * 清洗浏览器上传的原始文件名，只保留安全文件名部分。
     */
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

    /**
     * 规范化内部下载地址的基础 URL。
     */
    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "http://localhost:8080";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 兼容不同前端枚举写法并转换为内部状态值。
     */
    private String normalizeStatus(String status) {
        return status.toLowerCase(Locale.ROOT)
            .replace("upload_", "");
    }

    /**
     * 将知识文件实体转换为接口返回 DTO。
     */
    private KnowledgeFileDTO toDTO(KnowledgeOriginalFile record) {
        KnowledgeFileDTO dto = new KnowledgeFileDTO();
        dto.setId(record.getId());
        dto.setDatasetId(record.getDatasetId());
        dto.setOriginalFilename(record.getOriginalFilename());
        dto.setFileSuffix(record.getFileSuffix());
        dto.setFileSize(record.getFileSize());
        dto.setUploadStatus(toUploadStatus(record.getUploadStatus()));
        dto.setIsUploadSuccess(Boolean.TRUE.equals(record.getIsUploadSuccess()));
        dto.setFailureReason(record.getFailureReason());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        return dto;
    }

    /**
     * 将内部上传状态转换为对外枚举。
     */
    private String toUploadStatus(String status) {
        return switch (status) {
            case UPLOAD_SUCCESS -> "UPLOAD_SUCCESS";
            case UPLOAD_FAILED -> "UPLOAD_FAILED";
            default -> "UPLOADING";
        };
    }

}

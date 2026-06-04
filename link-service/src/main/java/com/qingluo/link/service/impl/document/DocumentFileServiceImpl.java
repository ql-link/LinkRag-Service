package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.response.DocumentFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.DocumentFileDownloadResource;
import com.qingluo.link.service.DocumentFileService;
import com.qingluo.link.service.DocumentFileRuntimeConfigService;
import com.qingluo.link.service.delete.DocumentDeleteNotifier;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import java.io.File;
import java.nio.file.Path;
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
 * 文档文件服务实现，负责原文件上传、查询、删除和解析任务投递。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentFileServiceImpl implements DocumentFileService {

    private static final String UPLOADING = "uploading";
    private static final String UPLOAD_SUCCESS = "success";
    private static final String UPLOAD_FAILED = "failed";
    private final DatasetMapper datasetMapper;
    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentDeleteNotifier deleteNotifier;
    private final IOssService ossService;
    private final PrivateFileResolver privateFileResolver;
    private final DocumentFileProperties properties;
    private final DocumentFileRuntimeConfigService documentFileRuntimeConfigService;
    private final DocumentUploadAsyncExecutor asyncExecutor;
    private final DocumentUploadTempStorage tempStorage;

    @Override
    @Transactional
    /**
     * 上传原始文档文件：同步阶段（鉴权/校验/同名处理/物化临时文件/落 uploading）后立即返回 uploading；
     * OSS 上传、终态回写与（parseImmediately 时的）解析投递在事务提交后于专用线程池异步完成。
     */
    public DocumentFileDTO upload(Long userId, Long datasetId, MultipartFile file, boolean parseImmediately) {
        assertOwnedDataset(userId, datasetId);
        validateFile(file);

        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        String suffix = extractSuffix(originalFilename);

        // 同名分流：撞 failed 复用旧行重置 uploading；撞 uploading/success 拦截 400；无同名则新建。
        DocumentOriginalFile record = resolveTargetRecord(userId, datasetId, originalFilename, suffix, file);

        // 物化临时文件：趁请求期 MultipartFile 仍有效取得所有权，供请求结束后的异步线程使用（同卷 rename，≈免费）。
        Path tempFile;
        try {
            tempFile = tempStorage.materialize(file);
        } catch (Exception e) {
            // 物化失败：抛出后事务回滚，撤销刚落库的 uploading 记录，不残留在途产物。
            log.error("Materialize upload temp file failed, userId={}, datasetId={}, fileName={}",
                userId, datasetId, originalFilename, e);
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        }

        String objectKey = buildObjectKey(userId, datasetId, originalFilename);
        DocumentUploadAsyncExecutor.UploadTask task = new DocumentUploadAsyncExecutor.UploadTask(
            record.getId(), tempFile, objectKey, file.getContentType(), parseImmediately, userId);

        // 事务提交后再提交异步任务，确保池线程能看到已提交的 uploading 记录；
        // 若事务回滚（如后续异常）则清理已物化的临时文件，避免泄漏。
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncExecutor.submit(task);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        tempStorage.delete(tempFile);
                    }
                }
            });
        } else {
            asyncExecutor.submit(task);
        }
        return toDTO(record);
    }

    /**
     * 同名分流（受唯一约束 uk_dataset_user_name_suffix 约束，只能复用旧行、不能插新行）：
     * <ul>
     *   <li>撞到的同名记录为 failed → 守卫更新复用该行（failed→uploading、清空上次产物/原因、刷新元数据）；</li>
     *   <li>撞到 uploading/success → 抛 400 拦截；</li>
     *   <li>无同名 → 插入新的 uploading 记录（并发同名由唯一约束兜底为 400）。</li>
     * </ul>
     */
    private DocumentOriginalFile resolveTargetRecord(
            Long userId, Long datasetId, String originalFilename, String suffix, MultipartFile file) {
        DocumentOriginalFile existing = documentOriginalFileMapper.selectOne(
            new LambdaQueryWrapper<DocumentOriginalFile>()
                .eq(DocumentOriginalFile::getUserId, userId)
                .eq(DocumentOriginalFile::getDatasetId, datasetId)
                .eq(DocumentOriginalFile::getOriginalFilename, originalFilename)
                .eq(DocumentOriginalFile::getFileSuffix, suffix));
        if (existing != null) {
            if (!UPLOAD_FAILED.equals(existing.getUploadStatus())) {
                throw new BusinessException(400, "当前数据集下已存在同名原文件，请先重命名后再上传", 400);
            }
            int reused = documentOriginalFileMapper.update(null, new LambdaUpdateWrapper<DocumentOriginalFile>()
                .eq(DocumentOriginalFile::getId, existing.getId())
                .eq(DocumentOriginalFile::getUploadStatus, UPLOAD_FAILED)
                .set(DocumentOriginalFile::getUploadStatus, UPLOADING)
                .set(DocumentOriginalFile::getIsUploadSuccess, false)
                .set(DocumentOriginalFile::getFailureReason, null)
                .set(DocumentOriginalFile::getObjectKey, null)
                .set(DocumentOriginalFile::getFileUrl, null)
                .set(DocumentOriginalFile::getFileSize, file.getSize())
                .set(DocumentOriginalFile::getContentType, file.getContentType())
                .set(DocumentOriginalFile::getBucketName, ossService.getBucketName(OssSavePlaceEnum.PRIVATE)));
            if (reused == 0) {
                // 并发：旧行已被他人复用或改状态。
                throw new BusinessException(400, "当前数据集下已存在同名原文件，请先重命名后再上传", 400);
            }
            existing.setUploadStatus(UPLOADING);
            existing.setIsUploadSuccess(false);
            existing.setFailureReason(null);
            existing.setObjectKey(null);
            existing.setFileUrl(null);
            existing.setFileSize(file.getSize());
            existing.setContentType(file.getContentType());
            return existing;
        }
        DocumentOriginalFile record = new DocumentOriginalFile();
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
            documentOriginalFileMapper.insert(record);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(400, "当前数据集下已存在同名原文件，请先重命名后再上传", 400);
        }
        return record;
    }

    @Override
    /**
     * 分页查询文档文件列表，并支持按状态筛选。
     */
    public PageResult<DocumentFileDTO> list(Long userId, Long datasetId, String uploadStatus, int page, int pageSize) {
        assertOwnedDataset(userId, datasetId);
        PageHelper.startPage(page, pageSize);
        LambdaQueryWrapper<DocumentOriginalFile> wrapper = new LambdaQueryWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getDatasetId, datasetId)
            .eq(DocumentOriginalFile::getUserId, userId)
            .orderByDesc(DocumentOriginalFile::getCreatedAt)
            .orderByDesc(DocumentOriginalFile::getId);
        if (StringUtils.hasText(uploadStatus)) {
            wrapper.eq(DocumentOriginalFile::getUploadStatus, normalizeStatus(uploadStatus));
        }
        List<DocumentOriginalFile> records = documentOriginalFileMapper.selectList(wrapper);
        PageInfo<DocumentOriginalFile> pageInfo = new PageInfo<>(records);
        return new PageResult<>(records.stream().map(this::toDTO).toList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    /**
     * 查询文档文件详情。
     */
    public DocumentFileDTO detail(Long userId, Long fileId) {
        return toDTO(getOwnedFile(userId, fileId));
    }

    @Override
    @Transactional
    /**
     * 隐性删除单个文件：软删原文件行（保留 OSS 原文件对象、不物理删行），提交后预留通知 Python
     * 删除其衍生产物（占位）。解析派生行（document_parse_file / document_parsed_log）交 Python 清理，
     * 本方法不再触碰。
     */
    public void delete(Long userId, Long fileId) {
        DocumentOriginalFile record = getOwnedFile(userId, fileId);

        // 软删该原文件：不删 OSS 对象、不物理删行；deleted_seq 置为自身 id，使死行退出唯一键“活名额”，
        // 支持删后同名重传（实体带 @TableLogic，MP 对 wrapper update 自动追加 is_deleted=0）。
        documentOriginalFileMapper.update(null, new LambdaUpdateWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getId, record.getId())
            .set(DocumentOriginalFile::getIsDeleted, true)
            .set(DocumentOriginalFile::getDeletedSeq, record.getId()));

        // 事务提交后再通知 Python 删衍生产物（file 范围，Python 按 original_file_id 删该文件产物）；回滚则不通知。
        notifyFileDeletedAfterCommit(record.getId(), record.getDatasetId(), userId);
    }

    /**
     * 删除事务提交后通知 Python 删除该原文件的衍生产物（file 范围）；处于事务中则注册 afterCommit（回滚不发），
     * 无事务时（如单元测试）直接调用。沿用上传链路的 afterCommit 模式。
     */
    private void notifyFileDeletedAfterCommit(Long originalFileId, Long datasetId, Long userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteNotifier.notifyFileDeleted(originalFileId, datasetId, userId);
                }
            });
        } else {
            deleteNotifier.notifyFileDeleted(originalFileId, datasetId, userId);
        }
    }

    @Override
    /**
     * 按文件标识和解析任务标识打开原始文件。
     */
    public DocumentFileDownloadResource openOriginalFile(Long fileId) {
        DocumentOriginalFile record = documentOriginalFileMapper.selectOne(new LambdaQueryWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getId, fileId));
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
        return new DocumentFileDownloadResource(file, record.getOriginalFilename(), record.getContentType());
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
     * 查询当前用户可访问的文档文件记录。
     */
    private DocumentOriginalFile getOwnedFile(Long userId, Long fileId) {
        DocumentOriginalFile record = documentOriginalFileMapper.selectOne(new LambdaQueryWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getId, fileId)
            .eq(DocumentOriginalFile::getUserId, userId));
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
        DocumentFileRuntimeConfig runtimeConfig = documentFileRuntimeConfigService.getCurrent();
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
     * 将文档文件实体转换为接口返回 DTO。
     */
    private DocumentFileDTO toDTO(DocumentOriginalFile record) {
        DocumentFileDTO dto = new DocumentFileDTO();
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

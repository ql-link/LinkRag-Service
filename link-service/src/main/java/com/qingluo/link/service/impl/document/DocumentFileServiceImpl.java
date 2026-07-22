package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.response.DocumentFileCapabilitiesDTO;
import com.qingluo.link.model.dto.response.DocumentFileDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.DocumentFileDownloadResource;
import com.qingluo.link.service.DocumentFileService;
import com.qingluo.link.service.DocumentFileRuntimeConfigService;
import com.qingluo.link.service.document.DocumentFileUploadCommand;
import com.qingluo.link.service.delete.DocumentDeleteNotifier;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import com.qingluo.link.service.impl.document.DocumentUploadTempStorage.ManagedBundle;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetManifestStore;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetMatchMode;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetObjectKeys;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetPackageProcessor;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetPackageProcessor.PreflightPlan;
import com.qingluo.link.service.impl.document.markdown.MarkdownUploadBundle;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String UPLOAD_SUCCESS = "success";
    private static final String UPLOAD_FAILED = "failed";
    private static final int ORIGINAL_FILENAME_MAX_LENGTH = 255;
    private final DatasetMapper datasetMapper;
    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentDeleteNotifier deleteNotifier;
    private final PrivateFileResolver privateFileResolver;
    private final DocumentFileProperties properties;
    private final DocumentFileRuntimeConfigService documentFileRuntimeConfigService;
    private final DocumentUploadAsyncExecutor asyncExecutor;
    private final DocumentUploadTempStorage tempStorage;
    private final DocumentUploadRecordWriter recordWriter;
    private final DocumentUploadStatusWriter statusWriter;
    private final MarkdownAssetPackageProcessor markdownAssetProcessor;
    private final MarkdownAssetManifestStore manifestStore;

    @Override
    public DocumentFileDTO upload(Long userId, Long datasetId, MultipartFile file, boolean parseImmediately) {
        return upload(userId, datasetId, new DocumentFileUploadCommand(
            file, parseImmediately, null, null, List.of(), List.of(), List.of()));
    }

    @Override
    public DocumentFileDTO upload(Long userId, Long datasetId, DocumentFileUploadCommand command) {
        assertOwnedDataset(userId, datasetId);
        MultipartFile file = command.file();
        assertFilePresent(file);

        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
        validateFile(file, originalFilename);
        String suffix = extractSuffix(originalFilename);
        boolean markdown = MarkdownAssetObjectKeys.isMarkdown(suffix);
        MarkdownAssetMatchMode matchMode = MarkdownAssetMatchMode.parse(command.matchMode());
        if (!markdown && hasAssetContext(command)) {
            throw new BusinessException(400, "仅 Markdown 文件支持配套图片", 400);
        }
        if (matchMode == null && hasCompanionParts(command)) {
            throw new BusinessException(ErrorCode.MARKDOWN_LOCAL_ASSET_REQUIRES_CONTEXT);
        }

        ManagedBundle managed = null;
        DocumentOriginalFile record = null;
        boolean submitted = false;
        try {
            managed = tempStorage.materializeBundle(
                file, command.assets(), command.assetRelativePaths());
            PreflightPlan plan = null;
            String recordFilename = originalFilename;
            if (markdown) {
                if (matchMode == null) {
                    if (!markdownAssetProcessor.scan(managed.sourceFile()).isEmpty()) {
                        throw new BusinessException(ErrorCode.MARKDOWN_LOCAL_ASSET_REQUIRES_CONTEXT);
                    }
                } else {
                    plan = markdownAssetProcessor.preflight(
                        managed.sourceFile(),
                        originalFilename,
                        matchMode,
                        command.documentPath(),
                        managed.assets(),
                        command.assetInventoryPaths());
                    recordFilename = plan.documentPath();
                }
            }

            record = recordWriter.prepareRecord(
                userId, datasetId, recordFilename, suffix, file.getSize(), file.getContentType());
            DocumentUploadAsyncExecutor.UploadTask task;
            DocumentFileDTO dto = toDTO(record);
            if (plan == null) {
                String objectKey = buildObjectKey(userId, datasetId, recordFilename);
                task = DocumentUploadAsyncExecutor.UploadTask.legacy(
                    record.getId(), userId, command.parseImmediately(), managed,
                    objectKey, file.getContentType());
            } else {
                MarkdownUploadBundle bundle = markdownAssetProcessor.finalizeForFile(
                    plan, userId, datasetId, record.getId(), managed.directory());
                dto.setAssetSummary(bundle.summary());
                task = DocumentUploadAsyncExecutor.UploadTask.markdown(
                    record.getId(), userId, command.parseImmediately(), managed, bundle);
            }
            asyncExecutor.submit(task);
            submitted = true;
            return dto;
        } catch (IOException e) {
            if (record != null) {
                statusWriter.markUploadFailed(record.getId(), "文件上传失败，请稍后重试");
            }
            throw new BusinessException(500, "文件上传失败，请稍后重试", 500);
        } catch (RuntimeException e) {
            if (record != null) {
                statusWriter.markUploadFailed(record.getId(), "文件上传失败，请稍后重试");
            }
            throw e;
        } finally {
            if (!submitted) {
                tempStorage.deleteAll(managed);
            }
        }
    }

    private boolean hasAssetContext(DocumentFileUploadCommand command) {
        return StringUtils.hasText(command.matchMode()) || StringUtils.hasText(command.documentPath())
            || hasCompanionParts(command) || !command.assetInventoryPaths().isEmpty();
    }

    private boolean hasCompanionParts(DocumentFileUploadCommand command) {
        return !command.assets().isEmpty() || !command.assetRelativePaths().isEmpty();
    }

    @Override
    public DocumentFileCapabilitiesDTO getCapabilities() {
        DocumentFileRuntimeConfig runtime = documentFileRuntimeConfigService.getCurrent();
        DocumentFileCapabilitiesDTO dto = new DocumentFileCapabilitiesDTO();
        dto.setFeatureEnabled(properties.isMarkdownAssetsEnabled());
        dto.getDocument().setAllowedSuffixes(runtime.getAllowedSuffixes().stream().sorted().toList());
        dto.getDocument().setMaxSizeBytes(runtime.getMaxSizeBytes());
        dto.getImage().setExtensions(List.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff"));
        dto.getImage().setMimeTypes(List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/tiff"));
        dto.getImage().setMaxAssetBytes(properties.getMarkdownAssetMaxBytes());
        dto.getImage().setMaxAssetCount(properties.getMarkdownAssetMaxCount());
        dto.getImage().setMaxInventoryCount(properties.getMarkdownInventoryMaxCount());
        dto.getImage().setMaxBundleBytes(properties.getMarkdownBundleMaxBytes());
        dto.getImage().setMaxPathLength(properties.getMarkdownAssetPathMaxLength());
        dto.getImage().setMaxDocumentPathLength(properties.getMarkdownDocumentPathMaxLength());
        dto.getZip().setMaxCompressedBytes(properties.getZipMaxCompressedBytes());
        dto.getZip().setMaxEntries(properties.getZipMaxEntries());
        dto.getZip().setMaxExpandedBytes(properties.getZipMaxExpandedBytes());
        dto.getZip().setMaxRatio(properties.getZipMaxRatio());
        dto.getZip().setMaxDepth(properties.getZipMaxDepth());
        dto.setMatchModes(List.of("FULL_PATH", "SHALLOW_BASENAME"));
        return dto;
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

    /**
     * 查询当前用户全局最近文档文件列表。
     */
    @Override
    public PageResult<DocumentFileDTO> listRecent(Long userId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        LambdaQueryWrapper<DocumentOriginalFile> wrapper = new LambdaQueryWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getUserId, userId)
            .orderByDesc(DocumentOriginalFile::getCreatedAt)
            .orderByDesc(DocumentOriginalFile::getId);
        List<DocumentOriginalFile> records = documentOriginalFileMapper.selectList(wrapper);
        PageInfo<DocumentOriginalFile> pageInfo = new PageInfo<>(records);
        return new PageResult<>(records.stream().map(this::toDTO).toList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    /**
     * 查询文档文件详情。
     */
    public DocumentFileDTO detail(Long userId, Long fileId) {
        DocumentOriginalFile record = getOwnedFile(userId, fileId);
        DocumentFileDTO dto = toDTO(record);
        if (MarkdownAssetObjectKeys.isV1NormalizedSource(record.getObjectKey())) {
            dto.setAssetSummary(manifestStore.readSummary(record));
        }
        return dto;
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
        File file = privateFileResolver.getPrivateFile(OssSavePlaceEnum.RAW, record.getObjectKey());
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
    private void assertFilePresent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
        }
    }

    /**
     * 校验上传文件格式和大小是否符合限制。
     */
    private void validateFile(MultipartFile file, String originalFilename) {
        DocumentFileRuntimeConfig runtimeConfig = documentFileRuntimeConfigService.getCurrent();
        String suffix = extractSuffix(originalFilename);
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
        normalized = normalized.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(400, "请选择要上传的文件", 400);
        }
        if (normalized.length() > ORIGINAL_FILENAME_MAX_LENGTH) {
            throw new BusinessException(400, "文件名长度不能超过255个字符", 400);
        }
        if (containsControlCharacter(normalized) || ".".equals(normalized) || "..".equals(normalized)) {
            throw new BusinessException(400, "文件名包含非法字符", 400);
        }
        return normalized;
    }

    private boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
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

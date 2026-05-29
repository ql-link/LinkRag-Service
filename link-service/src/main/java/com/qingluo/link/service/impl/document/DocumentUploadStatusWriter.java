package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.entity.DocumentParseFile;
import com.qingluo.link.service.DocumentParseTaskService;
import com.qingluo.link.service.config.DocumentFileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 上传终态回写（独立 bean）。
 *
 * <p>从异步线程（{@link DocumentUploadAsyncExecutor}）与超时扫描（{@link DocumentUploadStuckScanner}）
 * 跨 bean 调用，保证 {@code @Transactional} 经代理生效（避免在 {@code DocumentFileServiceImpl} 内
 * 自调用导致事务失效）。所有写入用<strong>状态守卫更新</strong>（{@code where upload_status='uploading'}），
 * 保证幂等与并发安全：谁先到谁赢，晚到者为空操作。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadStatusWriter {

    private static final String UPLOADING = "uploading";
    private static final String UPLOAD_SUCCESS = "success";
    private static final String UPLOAD_FAILED = "failed";

    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentParseFileMapper documentParseFileMapper;
    private final DocumentParseTaskService documentParseTaskService;
    private final DocumentFileProperties properties;

    /**
     * 异步 OSS 上传成功后回写 success（守卫更新）。
     *
     * <p>命中 0 行说明记录已非 uploading（多半被超时扫描置 failed）：此时 OSS 已有对象但 DB 不再认它，
     * 即<strong>孤儿对象</strong>——告警日志含 objectKey 留痕，首版不做补偿删除，且不投递解析。
     * 命中则初始化解析聚合，并在 {@code parseImmediately} 时于提交后投递自动解析。</p>
     *
     * <p>用 {@code REQUIRES_NEW}：本方法可能在调用方事务的 afterCommit 阶段被调用（如池满拒绝走 afterCommit），
     * 此时 REQUIRED 会参与正在完成、即将提交完毕的调用方事务而无法独立提交；REQUIRES_NEW 保证终态回写
     * 始终在独立事务中提交，与调用上下文（线程池线程 / afterCommit / 扫描线程）无关。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUploadSuccess(Long recordId, String objectKey, boolean parseImmediately, Long userId) {
        String fileUrl = normalizeBaseUrl(properties.getInternalBaseUrl())
            + "/api/v1/internal/files/" + recordId + "/content";
        int updated = documentOriginalFileMapper.update(null, new LambdaUpdateWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getId, recordId)
            .eq(DocumentOriginalFile::getUploadStatus, UPLOADING)
            .set(DocumentOriginalFile::getObjectKey, objectKey)
            .set(DocumentOriginalFile::getFileUrl, fileUrl)
            .set(DocumentOriginalFile::getUploadStatus, UPLOAD_SUCCESS)
            .set(DocumentOriginalFile::getIsUploadSuccess, true)
            .set(DocumentOriginalFile::getFailureReason, null));
        if (updated == 0) {
            log.warn("孤儿对象：上传成功回写时记录已非 uploading（可能已超时置 failed），objectKey={}, recordId={}",
                objectKey, recordId);
            return;
        }
        DocumentOriginalFile record = documentOriginalFileMapper.selectById(recordId);
        if (record == null) {
            return;
        }
        initializeParseFileIfAbsent(record);
        if (parseImmediately) {
            submitAutoParseAfterCommit(userId, record);
        }
    }

    /**
     * 置 failed（守卫更新）。供异步 OSS 失败、池满拒绝、uploading 超时扫描共用，命中 0 行即幂等跳过。
     *
     * <p>用 {@code REQUIRES_NEW}：池满拒绝在 upload() 事务 afterCommit 阶段调用本方法，REQUIRED 会参与正在
     * 提交完毕的调用方事务导致更新不落库；REQUIRES_NEW 保证置 failed 始终独立提交（否则记录会滞留 uploading
     * 直到超时扫描兜底）。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUploadFailed(Long recordId, String reason) {
        documentOriginalFileMapper.update(null, new LambdaUpdateWrapper<DocumentOriginalFile>()
            .eq(DocumentOriginalFile::getId, recordId)
            .eq(DocumentOriginalFile::getUploadStatus, UPLOADING)
            .set(DocumentOriginalFile::getUploadStatus, UPLOAD_FAILED)
            .set(DocumentOriginalFile::getIsUploadSuccess, false)
            .set(DocumentOriginalFile::getFailureReason, reason));
    }

    /**
     * 幂等创建解析聚合记录；并发下捕获唯一约束冲突，复查存在即视为成功。
     */
    private void initializeParseFileIfAbsent(DocumentOriginalFile file) {
        DocumentParseFile existing = documentParseFileMapper.selectOne(new LambdaQueryWrapper<DocumentParseFile>()
            .eq(DocumentParseFile::getDocumentOriginalFileId, file.getId()));
        if (existing != null) {
            return;
        }
        DocumentParseFile parseFile = new DocumentParseFile();
        parseFile.setDocumentOriginalFileId(file.getId());
        parseFile.setDatasetId(file.getDatasetId());
        parseFile.setUserId(file.getUserId());
        parseFile.setOriginalFilename(file.getOriginalFilename());
        parseFile.setParseCount(0);
        try {
            documentParseFileMapper.insert(parseFile);
        } catch (DataIntegrityViolationException e) {
            if (documentParseFileMapper.selectOne(new LambdaQueryWrapper<DocumentParseFile>()
                .eq(DocumentParseFile::getDocumentOriginalFileId, file.getId())) == null) {
                throw e;
            }
        }
    }

    /**
     * 在当前事务提交后投递自动解析任务，确保解析只针对已成功上传并落库的文件。
     */
    private void submitAutoParseAfterCommit(Long userId, DocumentOriginalFile file) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentParseTaskService.submitAutoParseAfterUpload(userId, file);
                }
            });
            return;
        }
        documentParseTaskService.submitAutoParseAfterUpload(userId, file);
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
}

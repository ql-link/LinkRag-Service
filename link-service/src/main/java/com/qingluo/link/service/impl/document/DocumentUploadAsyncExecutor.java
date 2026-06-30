package com.qingluo.link.service.impl.document;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 文档上传异步编排：在 {@code documentUploadExecutor} 专用线程池内完成 OSS 上传、终态回写与临时文件清理。
 *
 * <p>提交发生在 {@code DocumentFileServiceImpl.upload} 的事务 afterCommit（仍在请求线程）；池+队列满被拒
 * （AbortPolicy 抛 {@link RejectedExecutionException}）时把记录置 failed，不退回请求线程同步执行。
 * 写库委托给 {@link DocumentUploadStatusWriter}（独立 bean，{@code @Transactional} 生效），OSS 网络 IO
 * 不占用任何 DB 事务。</p>
 */
@Component
@Slf4j
public class DocumentUploadAsyncExecutor {

    private final IOssService ossService;
    private final DocumentUploadStatusWriter statusWriter;
    private final DocumentUploadTempStorage tempStorage;
    private final Executor documentUploadExecutor;

    public DocumentUploadAsyncExecutor(IOssService ossService,
                                       DocumentUploadStatusWriter statusWriter,
                                       DocumentUploadTempStorage tempStorage,
                                       @Qualifier("documentUploadExecutor") Executor documentUploadExecutor) {
        this.ossService = ossService;
        this.statusWriter = statusWriter;
        this.tempStorage = tempStorage;
        this.documentUploadExecutor = documentUploadExecutor;
    }

    /**
     * 提交异步上传任务。池+队列满被拒 → 置 failed + 清临时文件（不退回同步）。
     */
    public void submit(UploadTask task) {
        try {
            documentUploadExecutor.execute(() -> runUpload(task));
        } catch (RejectedExecutionException e) {
            log.warn("Document upload rejected by full pool, mark failed. recordId={}, objectKey={}",
                task.recordId(), task.objectKey(), e);
            statusWriter.markUploadFailed(task.recordId(), "服务繁忙，请稍后重试");
            tempStorage.delete(task.tempFile());
        }
    }

    /**
     * 池线程内执行：OSS 上传 → 回写终态 → 清理临时文件。任何异常都不外泄出池线程。
     */
    void runUpload(UploadTask task) {
        try {
            String result = ossService.upload2PreviewUrl(
                OssSavePlaceEnum.RAW, task.tempFile().toFile(), task.contentType(), task.objectKey());
            if (!StringUtils.hasText(result)) {
                statusWriter.markUploadFailed(task.recordId(), "文件上传失败，请稍后重试");
                return;
            }
            try {
                statusWriter.markUploadSuccess(task.recordId(), result, task.parseImmediately(), task.userId());
            } catch (Exception e) {
                // OSS 成功但 DB 回写失败 → 孤儿对象：留痕含 objectKey，记录仍 uploading 由超时扫描兜底，首版不补偿删除。
                log.warn("孤儿对象：OSS 上传成功但 DB 回写失败，objectKey={}, recordId={}",
                    task.objectKey(), task.recordId(), e);
            }
        } catch (Exception e) {
            log.error("Async document upload failed, recordId={}, objectKey={}",
                task.recordId(), task.objectKey(), e);
            try {
                statusWriter.markUploadFailed(task.recordId(), "文件上传失败，请稍后重试");
            } catch (Exception ex) {
                log.error("Mark upload failed after async error also failed, recordId={}", task.recordId(), ex);
            }
        } finally {
            tempStorage.delete(task.tempFile());
        }
    }

    /**
     * 异步上传上下文：请求线程构造，携带池线程所需的全部数据（不再依赖请求期对象）。
     */
    public record UploadTask(Long recordId, Path tempFile, String objectKey, String contentType,
                             boolean parseImmediately, Long userId) {
    }
}

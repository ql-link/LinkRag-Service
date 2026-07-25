package com.qingluo.link.service.impl.document;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.service.impl.document.DocumentUploadTempStorage.ManagedBundle;
import com.qingluo.link.service.impl.document.markdown.MarkdownUploadBundle;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class DocumentUploadAsyncExecutor {

    private final IOssService ossService;
    private final DocumentUploadStatusWriter statusWriter;
    private final DocumentUploadTempStorage tempStorage;
    private final Executor documentUploadExecutor;

    public DocumentUploadAsyncExecutor(
            IOssService ossService,
            DocumentUploadStatusWriter statusWriter,
            DocumentUploadTempStorage tempStorage,
            @Qualifier("documentUploadExecutor") Executor documentUploadExecutor) {
        this.ossService = ossService;
        this.statusWriter = statusWriter;
        this.tempStorage = tempStorage;
        this.documentUploadExecutor = documentUploadExecutor;
    }

    public void submit(UploadTask task) {
        try {
            documentUploadExecutor.execute(() -> runUpload(task));
        } catch (RejectedExecutionException e) {
            log.warn("Document upload rejected, recordId={}", task.recordId(), e);
            statusWriter.markUploadFailed(task.recordId(), "服务繁忙，请稍后重试");
            cleanup(task);
        }
    }

    void runUpload(UploadTask task) {
        try {
            String objectKey = task.markdownBundle() == null
                ? uploadLegacy(task)
                : uploadMarkdownBundle(task.markdownBundle());
            if (!StringUtils.hasText(objectKey)) {
                statusWriter.markUploadFailed(task.recordId(), "文件上传失败，请稍后重试");
                return;
            }
            try {
                if (task.markdownBundle() == null) {
                    statusWriter.markUploadSuccess(
                        task.recordId(), objectKey, task.parseImmediately(), task.userId());
                } else {
                    statusWriter.markUploadSuccess(
                        task.recordId(),
                        objectKey,
                        task.parseImmediately(),
                        task.userId(),
                        task.markdownBundle().hasBlockingIssues());
                }
            } catch (Exception e) {
                log.warn("OSS objects committed but upload status write failed, recordId={}",
                    task.recordId(), e);
            }
        } catch (Exception e) {
            log.error("Async document upload failed, recordId={}", task.recordId(), e);
            try {
                statusWriter.markUploadFailed(task.recordId(), "文件上传失败，请稍后重试");
            } catch (Exception writeError) {
                log.error("Mark upload failed also failed, recordId={}", task.recordId(), writeError);
            }
        } finally {
            cleanup(task);
        }
    }

    private String uploadLegacy(UploadTask task) {
        return ossService.upload2PreviewUrl(
            OssSavePlaceEnum.RAW,
            task.sourceFile().toFile(),
            task.contentType(),
            task.objectKey());
    }

    private String uploadMarkdownBundle(MarkdownUploadBundle bundle) {
        if (!ossService.deleteFile(OssSavePlaceEnum.RAW, bundle.manifestObjectKey())) {
            return null;
        }
        if (!upload(bundle.originalMarkdown(), "text/markdown; charset=utf-8", bundle.originalObjectKey())) {
            return null;
        }
        for (MarkdownUploadBundle.ImageUpload image : bundle.images()) {
            if (!upload(image.tempFile(), image.contentType(), image.objectKey())) {
                return null;
            }
        }
        if (!upload(bundle.normalizedMarkdown(), "text/markdown; charset=utf-8",
            bundle.normalizedObjectKey())) {
            return null;
        }
        if (!upload(bundle.manifestFile(), "application/json; charset=utf-8",
            bundle.manifestObjectKey())) {
            return null;
        }
        return bundle.normalizedObjectKey();
    }

    private boolean upload(Path path, String contentType, String objectKey) {
        return StringUtils.hasText(ossService.upload2PreviewUrl(
            OssSavePlaceEnum.RAW, path.toFile(), contentType, objectKey));
    }

    private void cleanup(UploadTask task) {
        if (task.managedBundle() != null) {
            tempStorage.deleteAll(task.managedBundle());
        } else {
            tempStorage.delete(task.legacyTempFile());
        }
    }

    public record UploadTask(
        Long recordId,
        Long userId,
        boolean parseImmediately,
        ManagedBundle managedBundle,
        Path legacyTempFile,
        String objectKey,
        String contentType,
        MarkdownUploadBundle markdownBundle
    ) {
        public UploadTask(
                Long recordId,
                Path tempFile,
                String objectKey,
                String contentType,
                boolean parseImmediately,
                Long userId) {
            this(recordId, userId, parseImmediately, null, tempFile, objectKey, contentType, null);
        }

        public static UploadTask legacy(
                Long recordId,
                Long userId,
                boolean parseImmediately,
                ManagedBundle bundle,
                String objectKey,
                String contentType) {
            return new UploadTask(
                recordId, userId, parseImmediately, bundle, null, objectKey, contentType, null);
        }

        public static UploadTask markdown(
                Long recordId,
                Long userId,
                boolean parseImmediately,
                ManagedBundle bundle,
                MarkdownUploadBundle markdownBundle) {
            return new UploadTask(
                recordId,
                userId,
                parseImmediately,
                bundle,
                null,
                markdownBundle.normalizedObjectKey(),
                "text/markdown; charset=utf-8",
                markdownBundle);
        }

        public Path sourceFile() {
            return managedBundle == null ? legacyTempFile : managedBundle.sourceFile();
        }
    }
}

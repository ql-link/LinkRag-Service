package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.core.trace.TraceContext;
import com.qingluo.link.service.config.DocumentUploadAsyncProperties;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 在途上传超时兜底扫描。
 *
 * <p>进程内线程池不持久：进程重启/崩溃会让在途上传永久卡在 uploading。定时扫描创建后超过阈值仍为
 * uploading 的记录并置 failed（自愈到用户可重试态）。复用 {@link DocumentUploadStatusWriter#markUploadFailed}
 * 的<strong>状态守卫更新</strong>，即便记录在扫描与处置之间刚转终态也不会被误改。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadStuckScanner {

    private static final String UPLOADING = "uploading";

    private final DocumentOriginalFileMapper documentOriginalFileMapper;
    private final DocumentUploadStatusWriter statusWriter;
    private final DocumentUploadAsyncProperties properties;

    @Scheduled(fixedDelayString = "${tolink.document-file.upload-async.scan-interval-ms:60000}")
    public void scan() {
        TraceContext.startNew();
        try {
            LocalDateTime cutoff = LocalDateTime.now().minus(properties.getStuckThreshold());
            List<DocumentOriginalFile> stuck = documentOriginalFileMapper.selectList(
                new LambdaQueryWrapper<DocumentOriginalFile>()
                    .eq(DocumentOriginalFile::getUploadStatus, UPLOADING)
                    .lt(DocumentOriginalFile::getCreatedAt, cutoff));
            if (stuck == null || stuck.isEmpty()) {
                return;
            }
            for (DocumentOriginalFile record : stuck) {
                // 单条隔离，避免一条异常中断整批扫描。
                try {
                    statusWriter.markUploadFailed(record.getId(), "上传超时，请重试");
                    log.warn("Upload stuck in uploading over threshold, marked failed. recordId={}, datasetId={}, createdAt={}",
                        record.getId(), record.getDatasetId(), record.getCreatedAt());
                } catch (Exception e) {
                    log.warn("Mark stuck upload failed for one record, recordId={}", record.getId(), e);
                }
            }
        } finally {
            TraceContext.clear();
        }
    }
}

package com.qingluo.link.service.config;

import java.io.File;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档上传异步化配置：{@code tolink.document-file.upload-async.*}
 */
@Component
@ConfigurationProperties(prefix = "tolink.document-file.upload-async")
public class DocumentUploadAsyncProperties {

    /**
     * 本地临时文件目录。
     *
     * <p>须与容器 multipart 临时目录<strong>同卷</strong>，使 {@code MultipartFile.transferTo} 走
     * rename（O(1)、≈免费）而非跨盘字节拷贝。默认置于 {@code java.io.tmpdir} 下（与容器默认 multipart
     * 临时目录同卷）。</p>
     */
    private String tempDir =
        System.getProperty("java.io.tmpdir") + File.separator + "tolink" + File.separator + "document-upload";

    /**
     * uploading 超时阈值：超过该时长仍停在 uploading 视为异常并置 failed（自愈到可重试态）。
     * 默认 10 分钟，远大于正常 OSS 上传耗时，避免误杀仍在途的正常任务。
     */
    private Duration stuckThreshold = Duration.ofMinutes(10);

    /** 超时扫描间隔（毫秒）。{@code @Scheduled} 直接引用同名配置占位符；此处作记录与默认值。 */
    private long scanIntervalMs = 60_000L;

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public Duration getStuckThreshold() {
        return stuckThreshold;
    }

    public void setStuckThreshold(Duration stuckThreshold) {
        this.stuckThreshold = stuckThreshold;
    }

    public long getScanIntervalMs() {
        return scanIntervalMs;
    }

    public void setScanIntervalMs(long scanIntervalMs) {
        this.scanIntervalMs = scanIntervalMs;
    }
}

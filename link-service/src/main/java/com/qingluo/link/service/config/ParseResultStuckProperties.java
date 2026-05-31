package com.qingluo.link.service.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 卡住任务扫描配置：tolink.parse-result.stuck.*
 *
 * <p>文件上传解析是用户在线等待场景，超过阈值仍未出终态即视为异常。
 * 阈值默认 5 分钟，支持按数据集（dataset_id）覆盖，未配置回落默认值。</p>
 */
@Component
@ConfigurationProperties(prefix = "tolink.parse-result.stuck")
public class ParseResultStuckProperties {

    /** 默认超时阈值，缺省 5 分钟。 */
    private Duration defaultThreshold = Duration.ofMinutes(5);

    /** 扫描间隔（毫秒），由 @Scheduled 直接引用同名配置；此处仅作记录与默认值。 */
    private long scanIntervalMs = 60_000L;

    /** 按数据集覆盖的阈值：key=dataset_id，value=阈值（如 20m）。 */
    private Map<Long, Duration> datasetThresholds = new LinkedHashMap<>();

    public Duration getDefaultThreshold() {
        return defaultThreshold;
    }

    public void setDefaultThreshold(Duration defaultThreshold) {
        this.defaultThreshold = defaultThreshold;
    }

    public long getScanIntervalMs() {
        return scanIntervalMs;
    }

    public void setScanIntervalMs(long scanIntervalMs) {
        this.scanIntervalMs = scanIntervalMs;
    }

    public Map<Long, Duration> getDatasetThresholds() {
        return datasetThresholds;
    }

    public void setDatasetThresholds(Map<Long, Duration> datasetThresholds) {
        this.datasetThresholds = datasetThresholds;
    }

    /**
     * 取指定数据集的阈值，未配置回落默认值。
     */
    public Duration thresholdOf(Long datasetId) {
        if (datasetId == null) {
            return defaultThreshold;
        }
        return datasetThresholds.getOrDefault(datasetId, defaultThreshold);
    }

    /**
     * 所有阈值中的最小值，用于扫描粗筛（先按最宽松条件捞，再逐条精确判定）。
     */
    public Duration minThreshold() {
        Duration min = defaultThreshold;
        for (Duration d : datasetThresholds.values()) {
            if (d != null && d.compareTo(min) < 0) {
                min = d;
            }
        }
        return min;
    }
}

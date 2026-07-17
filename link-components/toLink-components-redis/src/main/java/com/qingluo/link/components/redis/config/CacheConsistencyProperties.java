package com.qingluo.link.components.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 缓存一致性统一配置。
 *
 * <p>集中管理同步删缓存超时预算、空值缓存 TTL、TTL 抖动和读保护等待时间，
 * 供业务缓存 owner service 与异步补偿消费者统一复用。</p>
 */
@ConfigurationProperties(prefix = CacheConsistencyProperties.PREFIX)
public class CacheConsistencyProperties {

    public static final String PREFIX = "tolink.cache-consistency";

    private boolean enabled = true;
    /**
     * 兼容保留字段。
     *
     * <p>当前仅保留用于配置绑定兼容；主流程第一次删缓存的失败语义
     * 已统一为“数据库写成功后不再改变请求结果”，不再由该字段决定是否抛错。</p>
     */
    private boolean syncDeleteRequired = true;
    private long syncDeleteMaxWaitMs = 600L;
    private long syncDeleteRetryIntervalMs = 100L;
    private long nullCacheTtlSeconds = 60L;
    private long ttlJitterSeconds = 300L;
    private long loadWaitMs = 50L;
    private long loadLockTtlMs = 5_000L;
    private long fenceTtlSeconds = 2_592_000L;
    private Cdc cdc = new Cdc();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSyncDeleteRequired() {
        return syncDeleteRequired;
    }

    public void setSyncDeleteRequired(boolean syncDeleteRequired) {
        this.syncDeleteRequired = syncDeleteRequired;
    }

    public long getSyncDeleteMaxWaitMs() {
        return syncDeleteMaxWaitMs;
    }

    public void setSyncDeleteMaxWaitMs(long syncDeleteMaxWaitMs) {
        this.syncDeleteMaxWaitMs = syncDeleteMaxWaitMs;
    }

    public long getSyncDeleteRetryIntervalMs() {
        return syncDeleteRetryIntervalMs;
    }

    public void setSyncDeleteRetryIntervalMs(long syncDeleteRetryIntervalMs) {
        this.syncDeleteRetryIntervalMs = syncDeleteRetryIntervalMs;
    }

    public long getNullCacheTtlSeconds() {
        return nullCacheTtlSeconds;
    }

    public void setNullCacheTtlSeconds(long nullCacheTtlSeconds) {
        this.nullCacheTtlSeconds = nullCacheTtlSeconds;
    }

    public long getTtlJitterSeconds() {
        return ttlJitterSeconds;
    }

    public void setTtlJitterSeconds(long ttlJitterSeconds) {
        this.ttlJitterSeconds = ttlJitterSeconds;
    }

    public long getLoadWaitMs() {
        return loadWaitMs;
    }

    public void setLoadWaitMs(long loadWaitMs) {
        this.loadWaitMs = loadWaitMs;
    }

    public long getLoadLockTtlMs() {
        return loadLockTtlMs;
    }

    public void setLoadLockTtlMs(long loadLockTtlMs) {
        this.loadLockTtlMs = loadLockTtlMs;
    }

    public long getFenceTtlSeconds() {
        return fenceTtlSeconds;
    }

    public void setFenceTtlSeconds(long fenceTtlSeconds) {
        this.fenceTtlSeconds = fenceTtlSeconds;
    }

    public Cdc getCdc() {
        return cdc;
    }

    public void setCdc(Cdc cdc) {
        this.cdc = cdc;
    }

    public static class Cdc {

        private boolean enabled;
        private boolean mappingsEnabled;
        private boolean consumerTargetsReady;
        private String database = "tolink_rag_db";
        private String sourceTopic;
        private String groupId = "tolink-cdc-bridge";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMappingsEnabled() {
            return mappingsEnabled;
        }

        public void setMappingsEnabled(boolean mappingsEnabled) {
            this.mappingsEnabled = mappingsEnabled;
        }

        public boolean isConsumerTargetsReady() {
            return consumerTargetsReady;
        }

        public void setConsumerTargetsReady(boolean consumerTargetsReady) {
            this.consumerTargetsReady = consumerTargetsReady;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getSourceTopic() {
            return sourceTopic;
        }

        public void setSourceTopic(String sourceTopic) {
            this.sourceTopic = sourceTopic;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }
    }
}

package com.qingluo.link.core.config;

/**
 * 通用线程池参数模板，供 {@code thread-pool.<池名>} 复用。
 *
 * <p>校验集中在 {@link #validate()}：在 bean 创建期 fail-fast，参数非法直接抛
 * {@link IllegalArgumentException}，使绑定该配置的线程池 bean 创建失败 → 应用启动失败，
 * 避免把非法线程池带到运行期。</p>
 */
public class PoolProperties {

    private int corePoolSize = 5;
    private int maxPoolSize = 10;
    private int queueCapacity = 50;
    private int keepAliveSeconds = 60;
    private String threadNamePrefix = "pool-";

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    /**
     * 参数合法性校验：核心线程数为正、最大线程数为正且不小于核心数、队列与空闲时间非负、线程名前缀非空。
     * 任一不满足即抛 {@link IllegalArgumentException}（fail-fast）。
     */
    public void validate() {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("thread-pool core-pool-size must be > 0, got " + corePoolSize);
        }
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("thread-pool max-pool-size must be > 0, got " + maxPoolSize);
        }
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                "thread-pool max-pool-size(" + maxPoolSize + ") must be >= core-pool-size(" + corePoolSize + ")");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("thread-pool queue-capacity must be >= 0, got " + queueCapacity);
        }
        if (keepAliveSeconds < 0) {
            throw new IllegalArgumentException("thread-pool keep-alive-seconds must be >= 0, got " + keepAliveSeconds);
        }
        if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
            throw new IllegalArgumentException("thread-pool thread-name-prefix must not be blank");
        }
    }
}

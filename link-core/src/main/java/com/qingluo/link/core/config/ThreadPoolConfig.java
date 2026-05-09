package com.qingluo.link.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Author JiXu
 * @Date 2025/1/16 下午3:30
 * @ClassName: ThreadPoolConfig
 * @Description:
 */
@Configuration
public class ThreadPoolConfig {
    @Value("${thread-pool.core-pool-size}")
    private int corePoolSize;

    @Value("${thread-pool.max-pool-size}")
    private int maxPoolSize;

    @Value("${thread-pool.queue-capacity}")
    private int queueCapacity;

    @Value("${thread-pool.keep-alive-seconds}")
    private int keepAliveSeconds;

    @Value("${thread-pool.thread-name-prefix}")
    private String threadNamePrefix;

    @Bean({"customThreadPool", "knowledgeFileUploadExecutor"})
    public Executor customThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1. 核心线程数
        executor.setCorePoolSize(corePoolSize);
        // 2. 最大线程数
        executor.setMaxPoolSize(maxPoolSize);
        // 3. 任务队列容量
        executor.setQueueCapacity(queueCapacity);
        // 4. 线程空闲时间
        executor.setKeepAliveSeconds(keepAliveSeconds);
        // 5. 线程名称前缀
        executor.setThreadNamePrefix(threadNamePrefix);
        // 6. 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化线程池
        executor.initialize();
        return executor;
    }
}

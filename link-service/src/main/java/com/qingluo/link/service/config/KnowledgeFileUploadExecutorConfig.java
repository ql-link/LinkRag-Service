package com.qingluo.link.service.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 原文件上传线程池配置。
 *
 * <p>MinIO/OSS 上传属于阻塞 IO，不能在业务代码里临时 new Thread，
 * 也不应无限占用 Web 请求线程。这里集中定义上传线程池，
 * 让上传并发、队列长度和拒绝策略都可以被统一管理。
 */
@Configuration
public class KnowledgeFileUploadExecutorConfig {

    /**
     * 原文件上传专用线程池。
     *
     * <p>当前一期文件一般不大，请求线程最多等待 30 秒。
     * 当线程池和队列都满时直接拒绝，由业务层将上传记录置为 failed，
     * 用户后续可以基于同一唯一键重新上传并覆盖同一个 object_key。
     */
    @Bean("knowledgeFileUploadExecutor")
    public Executor knowledgeFileUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("knowledge-file-upload-");
        // 上传文件通常不大，但 OSS/MinIO 调用属于阻塞 IO；用独立线程池隔离 Web 请求线程和上传并发。
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        // 队列用于吸收短时间批量上传；超过队列后直接拒绝，让业务层按失败状态重试而不是无限堆积。
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}

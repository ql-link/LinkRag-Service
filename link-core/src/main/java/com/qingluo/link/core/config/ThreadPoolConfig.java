package com.qingluo.link.core.config;

import com.qingluo.link.core.trace.MdcTaskDecorator;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池配置（多池就绪）。
 *
 * <p>每个业务一个专用池，按 {@code thread-pool.<池名>.*} 配置，经 {@link #buildExecutor} 统一构建、
 * 各带独立拒绝策略与线程名。原 {@code customThreadPool} 通用兜底池已移除，避免不同业务共用一个池互相拖累。</p>
 */
@Configuration
@EnableConfigurationProperties(ThreadPoolProperties.class)
public class ThreadPoolConfig {

    private final ThreadPoolProperties properties;

    public ThreadPoolConfig(ThreadPoolProperties properties) {
        this.properties = properties;
    }

    /**
     * 文档上传专用线程池。
     *
     * <p>拒绝策略用 {@link ThreadPoolExecutor.AbortPolicy}：池+队列满时抛
     * {@link java.util.concurrent.RejectedExecutionException}，由提交方
     * （{@code DocumentFileServiceImpl.upload} 的 afterCommit）捕获后把记录置 failed，
     * 不沿用 CallerRunsPolicy 退回请求线程同步执行（否则过载时破坏“快速返回”保证）。</p>
     */
    @Bean("documentUploadExecutor")
    public Executor documentUploadExecutor() {
        return buildExecutor(properties.getDocumentUpload(), new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 通用线程池工厂：先 {@link PoolProperties#validate()} fail-fast，再按参数构建并初始化。
     * 供多池复用——未来新池只需新增 {@code @Bean} 调用本方法并传入各自的拒绝策略。
     */
    private ThreadPoolTaskExecutor buildExecutor(PoolProperties props, RejectedExecutionHandler handler) {
        props.validate();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setKeepAliveSeconds(props.getKeepAliveSeconds());
        executor.setThreadNamePrefix(props.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(handler);
        // 透传提交线程的 MDC（含 traceId），异步上传日志与发起请求同链路
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}

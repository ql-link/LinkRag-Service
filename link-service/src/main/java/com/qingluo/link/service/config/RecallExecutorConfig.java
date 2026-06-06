package com.qingluo.link.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.security.InternalJwtSigner;
import com.qingluo.link.core.security.RecallSessionJwtSigner;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 召回网关组件装配。
 *
 * <p>线程池为召回自建专用池（未复用 link-core 的多池框架 ThreadPoolConfig），以避免改动跨需求文件、
 * 保持召回功能内聚——这是相对 TD §8 的一处实现细化，记入实现报告。okhttp 用同步 {@code execute()} 在本池
 * 执行（并发由池控制），从而不受 okhttp dispatcher 默认 maxRequestsPerHost=5 的限制。</p>
 */
@Configuration
@RequiredArgsConstructor
public class RecallExecutorConfig {

    private final RecallProperties properties;

    /** 召回转发专用线程池：池+队列满时 AbortPolicy 抛 RejectedExecutionException，由 RecallServiceImpl 转 SSE error。 */
    @Bean("recallStreamExecutor")
    public Executor recallStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorCoreSize());
        executor.setMaxPoolSize(properties.getExecutorMaxSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("recall-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** 调用 Python 的 okhttp 客户端：callTimeout 作为整体超时，连接/读超时独立配置。 */
    @Bean("recallOkHttpClient")
    public OkHttpClient recallOkHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .callTimeout(properties.getStreamTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
    }

    /** 内部 JWT 签发器：密钥与有效期来自配置，复用容器的 ObjectMapper 序列化 claims。 */
    @Bean
    public InternalJwtSigner recallJwtSigner(ObjectMapper objectMapper) {
        return new InternalJwtSigner(properties.getInternalJwtSecret(), properties.getJwtExpSeconds(), objectMapper);
    }

    /**
     * 前端直连召回的 session JWT 签发器（LINK-104）：使用独立密钥。
     *
     * <p>启动期强校验（brief 决策⑥）：session 密钥非空且 ≠ 内部召回密钥（密码学隔离），否则 fail-fast，
     * 避免运行期签出无效或隔离失效的 token。</p>
     */
    @Bean
    public RecallSessionJwtSigner recallSessionJwtSigner(ObjectMapper objectMapper) {
        String sessionSecret = properties.getSessionJwtSecret();
        if (sessionSecret == null || sessionSecret.isEmpty()) {
            throw new IllegalStateException(
                "tolink.recall.session-jwt-secret 未配置（RECALL_SESSION_JWT_SECRET）：召回 session token 无法签发");
        }
        if (sessionSecret.equals(properties.getInternalJwtSecret())) {
            throw new IllegalStateException(
                "tolink.recall.session-jwt-secret 必须与 internal-jwt-secret 不同（密码学隔离）");
        }
        return new RecallSessionJwtSigner(sessionSecret, properties.getSessionJwtExpSeconds(), objectMapper);
    }
}

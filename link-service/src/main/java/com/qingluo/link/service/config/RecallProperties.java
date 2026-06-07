package com.qingluo.link.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 召回网关配置（{@code tolink.recall.*}）。
 *
 * <p>密钥与地址类配置通过 yml 的 {@code ${ENV:default}} 占位由环境变量注入；本地默认值仅用于联调。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tolink.recall")
public class RecallProperties {

    /** Python RAG 服务地址，用于拼接 /api/v1/internal/recall/stream。 */
    private String pythonBaseUrl = "http://localhost:8000";

    /** 内部 JWT HS256 共享密钥；Java 签发端与 Python 验签端必须一致。 */
    private String internalJwtSecret = "";

    /** 内部 JWT 短有效期（秒）。 */
    private long jwtExpSeconds = 30L;

    /**
     * 前端直连召回的 session JWT HS256 独立密钥（LINK-104）；与 Python {@code RECALL_SESSION_JWT_SECRET} 一致。
     * <b>必须 ≠ {@link #internalJwtSecret}</b>（密码学隔离），且非空——启动期由 RecallExecutorConfig 强校验。
     */
    private String sessionJwtSecret = "";

    /** session JWT 短有效期（秒），建议 30s（Python 强制校验 exp）。 */
    private long sessionJwtExpSeconds = 30L;

    /**
     * 前端可见的 Python 召回地址（公网/网关），独立于内部 {@link #pythonBaseUrl}。
     * 响应 streamUrl = sessionStreamBaseUrl + /api/v1/recall/stream。
     */
    private String sessionStreamBaseUrl = "http://localhost:8000";

    /** 等待 Python stream 的整体超时（毫秒），作为 okhttp callTimeout。 */
    private long streamTimeoutMs = 60_000L;

    /**
     * SseEmitter 超时相对 stream-timeout-ms 的缓冲（毫秒）。emitter 超时 = stream-timeout-ms + 本值，
     * 使其严格大于 okhttp callTimeout，保证超时由上游先触发并发出 RECALL_TIMEOUT，
     * 而非前端 SSE 先超时被静默关闭（避免两个等长计时器竞争）。
     */
    private long emitterTimeoutBufferMs = 5_000L;

    /** 连接 Python 的连接超时（毫秒）。 */
    private long connectTimeoutMs = 3_000L;

    /** 读取 Python 响应的读超时（毫秒）。 */
    private long readTimeoutMs = 60_000L;

    /** 每用户每分钟召回上限（单机 guava 限流）。 */
    private int rateLimitPerMinute = 10;

    /** 召回转发线程池核心线程数。 */
    private int executorCoreSize = 8;

    /** 召回转发线程池最大线程数。 */
    private int executorMaxSize = 32;

    /** 召回转发线程池队列容量。 */
    private int executorQueueCapacity = 64;
}

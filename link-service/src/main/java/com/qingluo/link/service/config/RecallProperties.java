package com.qingluo.link.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 召回配置（{@code tolink.recall.*}）。
 *
 * <p>当前仅保留「前端直连」召回 session token 签发所需配置（LINK-104）。旧召回网关链路
 * （Java 同步转发 Python 内部召回端点）于 LINK-122 废弃清理，其内部 JWT、okhttp 超时、限流、
 * 转发线程池等配置随之移除。</p>
 *
 * <p>密钥与地址类配置通过 yml 的 {@code ${ENV:default}} 占位由环境变量注入；本地默认值仅用于联调。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tolink.recall")
public class RecallProperties {

    /**
     * 前端直连召回的 session JWT HS256 独立密钥（LINK-104）；与 Python {@code RECALL_SESSION_JWT_SECRET} 一致。
     * 非空——启动期由 RecallExecutorConfig 强校验。
     */
    private String sessionJwtSecret = "";

    /** session JWT 短有效期（秒），建议 30s（Python 强制校验 exp）。 */
    private long sessionJwtExpSeconds = 30L;

    /**
     * 前端可见的 Python RAG 流式问答地址（公网/网关）。
     * 响应 streamUrl = sessionStreamBaseUrl + /api/v1/rag/stream（LINK-138：Python LINK-131 将对外端点由
     * /api/v1/recall/stream 改名为 RAG 流式问答 /api/v1/rag/stream）。
     */
    private String sessionStreamBaseUrl = "http://localhost:8000";
}

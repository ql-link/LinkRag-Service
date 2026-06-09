package com.qingluo.link.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.security.RecallSessionJwtSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 召回组件装配。
 *
 * <p>当前仅装配「前端直连」召回的 session JWT 签发器（LINK-104）。旧召回网关链路
 * （RecallController + OkHttpRecallUpstreamClient 等：Java 同步转发 Python 内部召回端点）已于 LINK-122
 * 废弃清理，其专用转发线程池、okhttp 客户端与内部 JWT 签发器随之移除。</p>
 */
@Configuration
@RequiredArgsConstructor
public class RecallExecutorConfig {

    private final RecallProperties properties;

    /**
     * 前端直连召回的 session JWT 签发器（LINK-104）：使用独立密钥。
     *
     * <p>启动期强校验（brief 决策⑥）：session 密钥非空，否则 fail-fast，避免运行期签出无效 token。</p>
     */
    @Bean
    public RecallSessionJwtSigner recallSessionJwtSigner(ObjectMapper objectMapper) {
        String sessionSecret = properties.getSessionJwtSecret();
        if (sessionSecret == null || sessionSecret.isEmpty()) {
            throw new IllegalStateException(
                "tolink.recall.session-jwt-secret 未配置（RECALL_SESSION_JWT_SECRET）：召回 session token 无法签发");
        }
        return new RecallSessionJwtSigner(sessionSecret, properties.getSessionJwtExpSeconds(), objectMapper);
    }
}

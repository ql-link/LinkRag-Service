package com.qingluo.link.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.security.AccessTokenJwtSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** access JWT 私钥加载与签发器装配；配置错误在启动阶段 fail-fast。 */
@Configuration
@RequiredArgsConstructor
public class AccessTokenConfig {

    private final AccessTokenProperties properties;

    @Bean
    @ConditionalOnProperty(prefix = "tolink.auth.access-token", name = "enabled", havingValue = "true")
    public AccessTokenJwtSigner accessTokenJwtSigner(ObjectMapper objectMapper) {
        if (properties.getTtlSeconds() <= 0) {
            throw new IllegalStateException("tolink.auth.access-token.ttl-seconds 必须大于 0");
        }
        if (properties.getAudiences() == null || properties.getAudiences().isEmpty()) {
            throw new IllegalStateException("tolink.auth.access-token.audiences 不能为空");
        }
        return new AccessTokenJwtSigner(
            loadPrivateKey(properties.getPrivateKeyPath()),
            properties.getIssuer(),
            properties.getAudiences(),
            properties.getTtlSeconds(),
            objectMapper
        );
    }

    private PrivateKey loadPrivateKey(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalStateException("JAVA_ACCESS_JWT_PRIVATE_KEY_PATH 未配置");
        }
        try {
            String pem = Files.readString(Path.of(pathValue));
            String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("access JWT PKCS#8 RSA 私钥读取失败", e);
        }
    }
}

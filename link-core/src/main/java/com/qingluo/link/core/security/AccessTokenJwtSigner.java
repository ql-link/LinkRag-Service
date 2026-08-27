package com.qingluo.link.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Java/Python 共用登录 access token 的 RS256 JWT 签发器。
 *
 * <p>Java 持有私钥并保持唯一签发权；Python 只分发公钥。token 不携带数据集范围，
 * Python 根据 {@code sub} 从共享数据库实时收敛资源权限。</p>
 */
public class AccessTokenJwtSigner {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final byte[] HEADER = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
        .getBytes(StandardCharsets.UTF_8);

    private final PrivateKey privateKey;
    private final String issuer;
    private final List<String> audiences;
    private final long ttlSeconds;
    private final ObjectMapper objectMapper;

    public AccessTokenJwtSigner(
        PrivateKey privateKey,
        String issuer,
        List<String> audiences,
        long ttlSeconds,
        ObjectMapper objectMapper
    ) {
        this.privateKey = privateKey;
        this.issuer = issuer;
        this.audiences = List.copyOf(audiences);
        this.ttlSeconds = ttlSeconds;
        this.objectMapper = objectMapper;
    }

    /** 签发 iss/aud/sub/token_use/role/iat/exp/jti 完整 access JWT。 */
    public String sign(long userId, String role, Instant now) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 必须为正整数");
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audiences);
        claims.put("sub", String.valueOf(userId));
        claims.put("token_use", "access");
        claims.put("role", role);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(ttlSeconds).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());

        String signingInput = encode(HEADER) + "." + encode(toJsonBytes(claims));
        return signingInput + "." + encode(sign(signingInput));
    }

    private byte[] toJsonBytes(Map<String, Object> claims) {
        try {
            return objectMapper.writeValueAsBytes(claims);
        } catch (Exception e) {
            throw new IllegalStateException("access JWT claims 序列化失败", e);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("access JWT 签名失败", e);
        }
    }

    private String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }
}

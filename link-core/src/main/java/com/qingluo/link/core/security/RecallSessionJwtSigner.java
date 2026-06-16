package com.qingluo.link.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 召回「前端直连」session token 的 HS256 JWT 签发器（手写实现，JDK 原生 {@link Mac} + Base64URL）。
 *
 * <p>本签发器面向前端直连 Python 召回 SSE 的链路（LINK-104），使用<b>独立密钥</b>。claims 形态：</p>
 *
 * <ul>
 *   <li>{@code aud=tolink-rag-frontend}</li>
 *   <li>{@code scope=recall:stream}</li>
 *   <li>带 {@code iat}、<b>不带 {@code jti}</b>（token 短期可复用，不做一次性/防重放）</li>
 * </ul>
 *
 * <p>claims 必须与 Python 验签端配置逐字一致（{@code RECALL_SESSION_JWT_ISSUER/AUDIENCE/SCOPE}）。
 * {@code dataset_ids} 是权威授权范围，由调用方填入用户真实有权访问的显式集合（非空），Python 完全信任其做越权判定。</p>
 *
 * <p>密钥编码：用 secret 字符串的 UTF-8 字节作为 HMAC key（需与 Python 验签端对齐）。</p>
 */
public class RecallSessionJwtSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final byte[] HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8);

    /** = Python RECALL_SESSION_JWT_ISSUER。 */
    private static final String ISS = "tolink-java";
    /** = Python RECALL_SESSION_JWT_AUDIENCE。 */
    private static final String AUD = "tolink-rag-frontend";
    /** = Python RECALL_SESSION_JWT_SCOPE。 */
    private static final String SCOPE = "recall:stream";

    private final byte[] secretKey;
    private final long expSeconds;
    private final ObjectMapper objectMapper;

    public RecallSessionJwtSigner(String secret, long expSeconds, ObjectMapper objectMapper) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.expSeconds = expSeconds;
        this.objectMapper = objectMapper;
    }

    /**
     * 签发 session JWT。claims：iss / aud / scope / sub / dataset_ids / iat / exp。
     *
     * @param userId     当前登录用户 ID（正整数），写入 sub（字符串）
     * @param datasetIds 已校验的显式授权范围（非空），写入 dataset_ids
     * @param now        当前时间（显式传入便于测试断言 iat/exp）
     */
    public String sign(long userId, List<Long> datasetIds, Instant now) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISS);
        claims.put("aud", AUD);
        claims.put("scope", SCOPE);
        claims.put("sub", String.valueOf(userId));
        claims.put("dataset_ids", datasetIds);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(expSeconds).getEpochSecond());

        String signingInput = encode(HEADER) + "." + encode(toJsonBytes(claims));
        return signingInput + "." + encode(hmacSha256(signingInput));
    }

    private byte[] toJsonBytes(Map<String, Object> claims) {
        try {
            return objectMapper.writeValueAsBytes(claims);
        } catch (Exception e) {
            throw new IllegalStateException("session JWT claims 序列化失败", e);
        }
    }

    private byte[] hmacSha256(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("session JWT 签名失败", e);
        }
    }

    private String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }
}

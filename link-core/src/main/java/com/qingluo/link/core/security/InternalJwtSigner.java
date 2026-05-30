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
 * 内部 HS256 JWT 签发器（手写实现，JDK 原生 {@link Mac} + Base64URL）。
 *
 * <p>不使用 jjwt 0.9.1：该版本在 Java 17 下依赖已被移除的 {@code javax.xml.bind.DatatypeConverter}
 * （触发 NoClassDefFoundError）。HS256 JWT 结构简单，手写更可控、可测，且无额外依赖。</p>
 *
 * <p>claims 与发往 Python 的 body 同源（sub = body.user_id、dataset_ids = body.dataset_ids），由调用方保证自洽。</p>
 *
 * <p>密钥编码：用 secret 字符串的 UTF-8 字节作为 HMAC key。<b>待确认（TD §12-2）</b>：需与 Python 验签端对齐
 * （UTF-8 字节 vs hex 解码字节），联调前敲定。</p>
 */
public class InternalJwtSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final byte[] HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8);

    private final byte[] secretKey;
    private final long expSeconds;
    private final ObjectMapper objectMapper;

    public InternalJwtSigner(String secret, long expSeconds, ObjectMapper objectMapper) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.expSeconds = expSeconds;
        this.objectMapper = objectMapper;
    }

    /**
     * 签发内部 JWT。claims：iss=tolink-java / aud=tolink-rag / sub / scope=recall:execute / dataset_ids / jti / exp。
     *
     * @param userId     当前登录用户 ID，写入 sub
     * @param datasetIds 已校验/展开后的授权范围（非空），写入 dataset_ids
     * @param requestId  本次请求 ID，写入 jti
     * @param now        当前时间（显式传入便于测试断言 exp）
     */
    public String sign(long userId, List<Long> datasetIds, String requestId, Instant now) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "tolink-java");
        claims.put("aud", "tolink-rag");
        claims.put("sub", String.valueOf(userId));
        claims.put("scope", "recall:execute");
        claims.put("dataset_ids", datasetIds);
        claims.put("jti", requestId);
        claims.put("exp", now.plusSeconds(expSeconds).getEpochSecond());

        String signingInput = encode(HEADER) + "." + encode(toJsonBytes(claims));
        return signingInput + "." + encode(hmacSha256(signingInput));
    }

    private byte[] toJsonBytes(Map<String, Object> claims) {
        try {
            return objectMapper.writeValueAsBytes(claims);
        } catch (Exception e) {
            throw new IllegalStateException("内部 JWT claims 序列化失败", e);
        }
    }

    private byte[] hmacSha256(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("内部 JWT 签名失败", e);
        }
    }

    private String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }
}

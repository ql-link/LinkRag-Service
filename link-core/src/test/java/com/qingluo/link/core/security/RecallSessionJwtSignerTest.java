package com.qingluo.link.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * session JWT 签发：验证 HS256、前端直连约定 claims（aud/scope/iat、无 jti）、exp，以及签名可被同密钥重算验证。
 */
class RecallSessionJwtSignerTest {

    private static final String SECRET = "test-session-secret-0123456789abcdef";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should_SignHs256JwtWithSessionClaims_When_Sign")
    void Should_SignHs256JwtWithSessionClaims_When_Sign() throws Exception {
        RecallSessionJwtSigner signer = new RecallSessionJwtSigner(SECRET, 30L, objectMapper);
        Instant now = Instant.parse("2026-06-06T00:00:00Z");

        String jwt = signer.sign(123L, List.of(1L, 2L), now);

        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);

        JsonNode header = objectMapper.readTree(urlDecode(parts[0]));
        assertThat(header.get("alg").asText()).isEqualTo("HS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");

        JsonNode claims = objectMapper.readTree(urlDecode(parts[1]));
        assertThat(claims.get("iss").asText()).isEqualTo("tolink-java");
        assertThat(claims.get("aud").asText()).isEqualTo("tolink-rag-frontend");
        assertThat(claims.get("scope").asText()).isEqualTo("recall:stream");
        assertThat(claims.get("sub").asText()).isEqualTo("123");
        assertThat(claims.get("iat").asLong()).isEqualTo(now.getEpochSecond());
        assertThat(claims.get("exp").asLong()).isEqualTo(now.plusSeconds(30L).getEpochSecond());
        assertThat(objectMapper.convertValue(claims.get("dataset_ids"), List.class)).containsExactly(1, 2);
        // 短期可复用：不下发 jti（不做一次性/防重放）。
        assertThat(claims.has("jti")).isFalse();

        // 用同一密钥重算 HMAC-SHA256(header.payload)，应等于第三段签名。
        assertThat(parts[2]).isEqualTo(hmac(parts[0] + "." + parts[1]));
    }

    @Test
    @DisplayName("Should_ProduceDifferentSignature_When_DifferentSecret")
    void Should_ProduceDifferentSignature_When_DifferentSecret() {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        String a = new RecallSessionJwtSigner(SECRET, 30L, objectMapper).sign(1L, List.of(1L), now);
        String b = new RecallSessionJwtSigner("another-secret-xxxxxxxxxxxxxxxxxxxx", 30L, objectMapper)
            .sign(1L, List.of(1L), now);
        // header.payload 相同但签名段不同（密钥隔离的体现）。
        assertThat(a.split("\\.")[2]).isNotEqualTo(b.split("\\.")[2]);
    }

    private byte[] urlDecode(String segment) {
        return Base64.getUrlDecoder().decode(segment);
    }

    private String hmac(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }
}

package com.qingluo.link.core.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenJwtSignerTest {

    @Test
    void shouldSignPythonCompatibleRs256Claims() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        ObjectMapper objectMapper = new ObjectMapper();
        AccessTokenJwtSigner signer = new AccessTokenJwtSigner(
            keyPair.getPrivate(),
            "tolink-java",
            List.of("tolink-java-api", "tolink-rag-api"),
            7200L,
            objectMapper
        );

        Instant now = Instant.ofEpochSecond(1_787_760_000L);
        String token = signer.sign(10000L, "ADMIN", now);
        String[] segments = token.split("\\.");

        assertThat(segments).hasSize(3);
        Map<String, Object> header = decodeJson(objectMapper, segments[0]);
        Map<String, Object> claims = decodeJson(objectMapper, segments[1]);
        assertThat(header).containsEntry("alg", "RS256");
        assertThat(claims)
            .containsEntry("iss", "tolink-java")
            .containsEntry("sub", "10000")
            .containsEntry("token_use", "access")
            .containsEntry("role", "ADMIN")
            .containsEntry("iat", 1_787_760_000)
            .containsEntry("exp", 1_787_767_200);
        assertThat(claims.get("aud"))
            .isEqualTo(List.of("tolink-java-api", "tolink-rag-api"));
        assertThat((String) claims.get("jti")).isNotBlank();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(segments[2]))).isTrue();
    }

    private Map<String, Object> decodeJson(ObjectMapper objectMapper, String segment) throws Exception {
        return objectMapper.readValue(
            Base64.getUrlDecoder().decode(segment),
            new TypeReference<Map<String, Object>>() { }
        );
    }
}

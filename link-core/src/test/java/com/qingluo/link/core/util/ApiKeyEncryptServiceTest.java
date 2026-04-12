package com.qingluo.link.core.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiKeyEncryptService 测试
 */
class ApiKeyEncryptServiceTest {

    private ApiKeyEncryptService encryptService;

    @BeforeEach
    void setUp() {
        encryptService = new ApiKeyEncryptService();
        encryptService.setSecretKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    @Test
    void should_EncryptAndDecrypt_When_ValidKey() {
        String original = "sk-test-api-key-12345";

        String encrypted = encryptService.encrypt(original);
        String decrypted = encryptService.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo(original);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void should_GenerateDifferentCiphertext_When_SamePlaintext() {
        String original = "sk-test-api-key";

        String encrypted1 = encryptService.encrypt(original);
        String encrypted2 = encryptService.encrypt(original);

        // IV 不同，密文应该不同
        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // 但解密结果相同
        assertThat(encryptService.decrypt(encrypted1)).isEqualTo(original);
        assertThat(encryptService.decrypt(encrypted2)).isEqualTo(original);
    }

    @Test
    void should_DecryptSuccessfully_When_EncryptedWithSameKey() {
        String original = "sk-openai-1234567890abcdef";
        String encrypted = encryptService.encrypt(original);

        String decrypted = encryptService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }
}
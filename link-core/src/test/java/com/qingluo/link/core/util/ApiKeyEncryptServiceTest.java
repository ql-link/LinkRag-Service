package com.qingluo.link.core.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyEncryptService AES-256-GCM 加密解密测试
 */
class ApiKeyEncryptServiceTest {

    private final ApiKeyEncryptService encryptService = new ApiKeyEncryptService();

    @Test
    void Should_EncryptAndDecrypt_When_ValidKeyProvided() {
        ReflectionTestUtils.setField(encryptService, "secretKey",
            "secret-for-python-compatible-test");

        String original = "sk-test-api-key-12345";
        String encrypted = encryptService.encrypt(original);

        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = encryptService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void Should_DecryptPythonCiphertext_When_PythonCompatibleFormatProvided() {
        ReflectionTestUtils.setField(encryptService, "secretKey",
            "secret-for-python-compatible-test");

        String encryptedByPython = "AAECAwQFBgcICQoLuWL+OuJBDdSJ+pFk4H8mZRDGPEgwvXATbsN9RnzNIUTLhXYc9Q==";

        assertEquals("sk-test-api-key-12345", encryptService.decrypt(encryptedByPython));
    }

    @Test
    void Should_ReturnMaskedKey_When_MaskApiKeyCalled() {
        // "sk-1234567890abcdef" 最后4个字符是 "cdef"
        String masked = encryptService.maskApiKey("sk-1234567890abcdef");
        assertEquals("sk-****....cdef", masked);
    }

    @Test
    void Should_ReturnFourStars_When_KeyTooShort() {
        String masked = encryptService.maskApiKey("sk-short");
        assertEquals("****", masked);
    }
}

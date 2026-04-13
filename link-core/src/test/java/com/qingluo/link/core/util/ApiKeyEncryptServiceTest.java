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
        // 设置测试用密钥（64位十六进制 = 32字节）
        ReflectionTestUtils.setField(encryptService, "secretKey",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        String original = "sk-test-api-key-12345";
        String encrypted = encryptService.encrypt(original);

        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = encryptService.decrypt(encrypted);
        assertEquals(original, decrypted);
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
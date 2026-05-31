package com.qingluo.link.core.dto.response;

import com.qingluo.link.model.dto.response.AuthResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/**
 * AuthResult 认证结果测试
 */
class AuthResultTest {

    @Test
    void Should_CreateAuthResult_When_CreatedWithParams() {
        AuthResult result = new AuthResult("token123", "Bearer", 604800, 10000L);

        assertEquals("token123", result.getAccessToken());
        assertEquals("Bearer", result.getTokenType());
        assertEquals(604800, result.getExpiresIn());
        assertEquals(10000L, result.getUserId());
    }

    @Test
    void Should_SetDefaultTokenType_When_Created() {
        AuthResult result = new AuthResult();
        assertEquals("Bearer", result.getTokenType());
    }
}
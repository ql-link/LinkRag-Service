package com.qingluo.link.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorCode 枚举类测试
 */
class ErrorCodeTest {

    @Test
    void Should_ContainAllErrorCodes() {
        // 验证关键错误码存在
        assertNotNull(ErrorCode.PROVIDER_NOT_FOUND);
        assertNotNull(ErrorCode.USER_CONFIG_NOT_FOUND);
        assertNotNull(ErrorCode.USER_NOT_FOUND);
        assertNotNull(ErrorCode.INVALID_PASSWORD);
        assertNotNull(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    void Should_HaveCorrectCodeValues() {
        assertEquals(10001, ErrorCode.PROVIDER_NOT_FOUND.getCode());
        assertEquals(10004, ErrorCode.USER_CONFIG_NOT_FOUND.getCode());
        assertEquals(20001, ErrorCode.USER_NOT_FOUND.getCode());
        assertEquals(20002, ErrorCode.INVALID_PASSWORD.getCode());
        assertEquals(50001, ErrorCode.UNKNOWN_ERROR.getCode());
    }

    @Test
    void Should_HaveCorrectHttpStatus() {
        assertEquals(404, ErrorCode.PROVIDER_NOT_FOUND.getHttpStatus());
        assertEquals(400, ErrorCode.INVALID_API_KEY.getHttpStatus());
        assertEquals(401, ErrorCode.INVALID_PASSWORD.getHttpStatus());
        assertEquals(403, ErrorCode.AUTH_DISABLED.getHttpStatus());
        assertEquals(409, ErrorCode.DUPLICATE_USER_CONFIG.getHttpStatus());
    }

    @Test
    void Should_HaveCorrectMessages() {
        assertEquals("系统厂商不存在", ErrorCode.PROVIDER_NOT_FOUND.getMessage());
        assertEquals("用户不存在", ErrorCode.USER_NOT_FOUND.getMessage());
        assertEquals("密码错误", ErrorCode.INVALID_PASSWORD.getMessage());
    }
}
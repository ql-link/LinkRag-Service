package com.qingluo.link.core.enums;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ErrorCode 枚举测试
 * TDD Red: 验证枚举值、错误码格式、消息非空
 */
class ErrorCodeTest {

    @Test
    void should_HasCorrectErrorCodeFormat_When_ErrorCodeIs10001() {
        // LLM 厂商相关错误码应为 10xxx
        ErrorCode errorCode = ErrorCode.PROVIDER_NOT_FOUND;
        assertThat(errorCode.getCode()).isBetween(10001, 19999);
    }

    @Test
    void should_HasCorrectErrorCodeFormat_When_ErrorCodeIs20001() {
        // 用户/认证/对话相关错误码应为 20xxx
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;
        assertThat(errorCode.getCode()).isBetween(20001, 29999);
    }

    @Test
    void should_HasCorrectErrorCodeFormat_When_ErrorCodeIs50001() {
        // 系统级错误码应为 50xxx
        ErrorCode errorCode = ErrorCode.UNKNOWN_ERROR;
        assertThat(errorCode.getCode()).isBetween(50001, 59999);
    }

    @Test
    void should_HasNonEmptyMessage_When_GettingMessage() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getMessage())
                .as("ErrorCode %s should have non-empty message", errorCode.name())
                .isNotBlank();
        }
    }

    @Test
    void should_HasValidEnumNames_When_EnumValuesExist() {
        // 验证 LLM 厂商 & 配置 错误码
        assertThat(ErrorCode.PROVIDER_NOT_FOUND.getCode()).isEqualTo(10001);
        assertThat(ErrorCode.PROVIDER_DISABLED.getCode()).isEqualTo(10002);
        assertThat(ErrorCode.PROVIDER_IN_USE.getCode()).isEqualTo(10003);
        assertThat(ErrorCode.USER_CONFIG_NOT_FOUND.getCode()).isEqualTo(10004);
        assertThat(ErrorCode.USER_CONFIG_DISABLED.getCode()).isEqualTo(10005);
        assertThat(ErrorCode.NO_DEFAULT_CONFIG.getCode()).isEqualTo(10006);
        assertThat(ErrorCode.INVALID_API_KEY.getCode()).isEqualTo(10007);
        assertThat(ErrorCode.MODEL_NOT_SUPPORTED.getCode()).isEqualTo(10008);
        assertThat(ErrorCode.DUPLICATE_USER_CONFIG.getCode()).isEqualTo(10009);

        // 验证用户/认证/对话错误码
        assertThat(ErrorCode.USER_NOT_FOUND.getCode()).isEqualTo(20001);
        assertThat(ErrorCode.INVALID_PASSWORD.getCode()).isEqualTo(20002);
        assertThat(ErrorCode.AUTH_DISABLED.getCode()).isEqualTo(20003);
        assertThat(ErrorCode.CONVERSATION_NOT_FOUND.getCode()).isEqualTo(20004);
        assertThat(ErrorCode.UNAUTHORIZED_ACCESS.getCode()).isEqualTo(20005);

        // 验证系统级错误码
        assertThat(ErrorCode.UNKNOWN_ERROR.getCode()).isEqualTo(50001);
    }
}
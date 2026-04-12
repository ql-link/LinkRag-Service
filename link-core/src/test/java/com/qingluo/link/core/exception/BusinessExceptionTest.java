package com.qingluo.link.core.exception;

import com.qingluo.link.core.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BusinessException 测试
 * TDD Red: 验证异常构造、错误码携带、消息格式化
 */
class BusinessExceptionTest {

    @Test
    void should_CarryErrorCode_When_CreatedWithErrorCodeOnly() {
        BusinessException exception = new BusinessException(ErrorCode.USER_NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(exception.getDetail()).isEqualTo(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    void should_CarryErrorCodeAndDetail_When_CreatedWithDetail() {
        BusinessException exception = new BusinessException(ErrorCode.USER_CONFIG_NOT_FOUND, "配置ID: xxx 不存在");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_CONFIG_NOT_FOUND);
        assertThat(exception.getDetail()).isEqualTo("配置ID: xxx 不存在");
    }

    @Test
    void should_CarryErrorCodeDetailAndCause_When_CreatedWithCause() {
        RuntimeException cause = new RuntimeException("原始错误");
        BusinessException exception = new BusinessException(ErrorCode.UNKNOWN_ERROR, "系统错误", cause);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNKNOWN_ERROR);
        assertThat(exception.getDetail()).isEqualTo("系统错误");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void should_FormatMessageWithCodeAndDetail() {
        BusinessException exception = new BusinessException(ErrorCode.INVALID_API_KEY, "API Key 格式不正确");

        assertThat(exception.getMessage()).contains("[10007]");
        assertThat(exception.getMessage()).contains("API Key 格式不正确");
    }
}
package com.qingluo.link.core.exception;

import com.qingluo.link.core.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务异常子类测试
 * TDD Red: 验证 AuthException, NotFoundException, ForbiddenException, ConflictException
 */
class SubclassExceptionsTest {

    @Test
    void should_AuthExceptionCarryCorrectErrorCode() {
        AuthException exception = new AuthException(ErrorCode.INVALID_PASSWORD, "密码错误");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PASSWORD);
        assertThat(exception.getDetail()).isEqualTo("密码错误");
    }

    @Test
    void should_NotFoundExceptionCarryCorrectErrorCode() {
        NotFoundException exception = new NotFoundException(ErrorCode.USER_NOT_FOUND, "用户不存在");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(exception.getDetail()).isEqualTo("用户不存在");
    }

    @Test
    void should_ForbiddenExceptionCarryCorrectErrorCode() {
        ForbiddenException exception = new ForbiddenException(ErrorCode.UNAUTHORIZED_ACCESS, "无权访问");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_ACCESS);
        assertThat(exception.getDetail()).isEqualTo("无权访问");
    }

    @Test
    void should_ConflictExceptionCarryCorrectErrorCode() {
        ConflictException exception = new ConflictException(ErrorCode.DUPLICATE_USER_CONFIG, "配置已存在");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_USER_CONFIG);
        assertThat(exception.getDetail()).isEqualTo("配置已存在");
    }
}
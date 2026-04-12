package com.qingluo.link.service;

import com.qingluo.link.core.dto.request.LoginRequest;
import com.qingluo.link.core.dto.request.RegisterRequest;
import com.qingluo.link.core.dto.response.AuthResult;
import com.qingluo.link.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthService 测试
 */
class AuthServiceTest {

    @Test
    void should_ReturnAuthResult_When_LoginSuccess() {
        // Given
        AuthService authService = null; // TODO: 注入 mock
        LoginRequest request = LoginRequest.builder()
            .username("testuser")
            .password("password123")
            .build();

        // When
        AuthResult result = authService.login(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getExpiresIn()).isEqualTo(604800);
    }

    @Test
    void should_ThrowException_When_UserNotFound() {
        // Given
        AuthService authService = null; // TODO: 注入 mock
        LoginRequest request = LoginRequest.builder()
            .username("nonexistent")
            .password("password")
            .build();

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void should_ThrowException_When_PasswordIncorrect() {
        // Given
        AuthService authService = null; // TODO: 注入 mock
        LoginRequest request = LoginRequest.builder()
            .username("testuser")
            .password("wrongpassword")
            .build();

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void should_RegisterSuccessfully_When_RequestValid() {
        // Given
        AuthService authService = null; // TODO: 注入 mock
        RegisterRequest request = RegisterRequest.builder()
            .username("newuser")
            .password("password123")
            .email("new@example.com")
            .nickname("New User")
            .build();

        // When
        authService.register(request);

        // Then - 无异常即为成功
    }
}
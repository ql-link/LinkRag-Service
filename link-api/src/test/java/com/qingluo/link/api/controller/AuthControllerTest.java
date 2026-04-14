package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.response.AuthResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthController 控制器测试
 * TDD Red 阶段：验证 Controller 调用 Service
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void Should_ReturnAuthResult_When_LoginSuccess() {
        // given
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");
        AuthResult expected = new AuthResult("token-123", "Bearer", 604800L, 1L);
        when(authService.login(any(LoginRequest.class))).thenReturn(expected);

        // when
        Result<AuthResult> result = authController.login(request);

        // then
        assertNotNull(result);
        assertEquals("token-123", result.getData().getAccessToken());
        assertEquals("Bearer", result.getData().getTokenType());
        assertEquals(604800L, result.getData().getExpiresIn());
        assertEquals(1L, result.getData().getUserId());
        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void Should_ReturnAuthResult_When_RegisterSuccess() {
        // given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setNickname("新用户");
        request.setEmail("new@test.com");
        AuthResult expected = new AuthResult("token-456", "Bearer", 604800L, 2L);
        when(authService.register(any(RegisterRequest.class))).thenReturn(expected);

        // when
        Result<AuthResult> result = authController.register(request);

        // then
        assertNotNull(result);
        assertEquals("token-456", result.getData().getAccessToken());
        assertEquals(2L, result.getData().getUserId());
        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void Should_ReturnOk_When_LogoutSuccess() {
        // given
        doNothing().when(authService).logout();

        // when
        Result<Void> result = authController.logout();

        // then
        assertNotNull(result);
        verify(authService).logout();
    }
}

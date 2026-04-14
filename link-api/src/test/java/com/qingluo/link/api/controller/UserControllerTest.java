package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UserController 控制器测试
 * TDD Red 阶段：验证 Controller 调用 Service
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private UserController userController;

    @Test
    void Should_ReturnUserProfile_When_GetProfileSuccess() {
        // given
        Long userId = 1L;
        UserProfileDTO expected = new UserProfileDTO();
        expected.setId(userId);
        expected.setUsername("admin");
        expected.setNickname("管理员");
        expected.setEmail("admin@test.com");
        expected.setRole("ADMIN");
        expected.setStatus(1);

        // Mock authService.getProfile 返回预期值
        doReturn(expected).when(authService).getProfile(any());

        // when - 直接调用（不使用 AuthContext）
        // 由于 UserController 内部会调用 AuthContext.getLoginUserIdOrThrow()
        // 当前实现会返回空 UserProfileDTO，所以这个测试会失败
        Result<UserProfileDTO> result = userController.getProfile();

        // then
        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals("admin", result.getData().getUsername());
        assertEquals("ADMIN", result.getData().getRole());

        // 验证 authService.getProfile 被调用
        verify(authService).getProfile(any());
    }
}

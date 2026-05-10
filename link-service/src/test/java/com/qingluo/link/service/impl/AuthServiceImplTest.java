package com.qingluo.link.service.impl;

import com.qingluo.link.core.exception.AuthException;
import com.qingluo.link.core.exception.ConflictException;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.LoginRequest;
import com.qingluo.link.model.dto.request.RegisterRequest;
import com.qingluo.link.model.dto.request.UpdateProfileRequest;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.model.enums.UserRole;
import com.qingluo.link.service.cache.UserCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserCacheService userCacheService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("Should_RejectDuplicateEmail_When_Register")
    void Should_RejectDuplicateEmail_When_Register() {
        given(sysUserMapper.selectByUsername("new-user")).willReturn(null);
        given(sysUserMapper.selectByEmail("used@test.com"))
            .willReturn(buildUser(2L, "bob", UserRole.USER));

        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-user");
        request.setPassword("password123");
        request.setEmail("used@test.com");

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ConflictException.class)
            .hasMessage("邮箱已被使用");

        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("Should_QueryByAccountAndRejectPassword_When_LoginWithEmail")
    void Should_QueryByAccountAndRejectPassword_When_LoginWithEmail() {
        SysUser user = buildUser(3L, "alice", UserRole.USER);
        user.setEmail("alice@test.com");
        user.setPasswordHash("encoded-password");
        given(sysUserMapper.selectByAccount("alice@test.com")).willReturn(user);
        given(passwordEncoder.matches("wrongpassword", "encoded-password")).willReturn(false);

        LoginRequest request = new LoginRequest();
        request.setAccount(" alice@test.com ");
        request.setPassword("wrongpassword");

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(AuthException.class)
            .hasMessage("密码错误");

        verify(sysUserMapper).selectByAccount("alice@test.com");
    }
    // ---- getProfile ----

    @Test
    @DisplayName("Should_ReturnFromCache_When_CacheHit")
    void Should_ReturnFromCache_When_CacheHit() {
        UserProfileDTO cached = buildDto(1L, "alice", UserRole.USER);
        given(userCacheService.getOrLoad(eq(1L), any())).willReturn(cached);

        UserProfileDTO result = authService.getProfile(1L);

        assertThat(result.getUsername()).isEqualTo("alice");
        verifyNoInteractions(sysUserMapper);
    }

    @Test
    @DisplayName("Should_QueryDbAndWriteCache_When_CacheMiss")
    void Should_QueryDbAndWriteCache_When_CacheMiss() {
        SysUser user = buildUser(2L, "bob", UserRole.ADMIN);
        given(sysUserMapper.selectById(2L)).willReturn(user);
        given(userCacheService.getOrLoad(eq(2L), any())).willAnswer(invocation -> {
            Object loaded = invocation.getArgument(1, java.util.function.Supplier.class).get();
            return loaded;
        });

        UserProfileDTO result = authService.getProfile(2L);

        assertThat(result.getUsername()).isEqualTo("bob");
        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(sysUserMapper).selectById(2L);
    }

    // ---- updateProfile ----

    @Test
    @DisplayName("Should_UpdateFieldsAndEvictCache_When_UserExists")
    void Should_UpdateFieldsAndEvictCache_When_UserExists() {
        SysUser user = buildUser(1L, "alice", UserRole.USER);
        given(sysUserMapper.selectById(1L)).willReturn(user);
        given(sysUserMapper.selectByEmailExcludingUserId("new@test.com", 1L)).willReturn(null);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("newNick");
        request.setEmail("new@test.com");
        authService.updateProfile(1L, request);

        verify(sysUserMapper).updateById(any(SysUser.class));
        verify(userCacheService).evict(1L);
    }

    @Test
    @DisplayName("Should_RejectDuplicateEmail_When_UpdateProfile")
    void Should_RejectDuplicateEmail_When_UpdateProfile() {
        SysUser user = buildUser(1L, "alice", UserRole.USER);
        user.setEmail("alice@test.com");
        given(sysUserMapper.selectById(1L)).willReturn(user);
        given(sysUserMapper.selectByEmailExcludingUserId("used@test.com", 1L))
            .willReturn(buildUser(2L, "bob", UserRole.USER));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("used@test.com");

        assertThatThrownBy(() -> authService.updateProfile(1L, request))
            .isInstanceOf(ConflictException.class)
            .hasMessage("邮箱已被使用");

        verify(sysUserMapper, never()).updateById(any(SysUser.class));
        verify(userCacheService, never()).evict(anyLong());
    }

    // ---- helpers ----

    private UserProfileDTO buildDto(Long id, String username, UserRole role) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(id);
        dto.setUsername(username);
        dto.setRole(role.name());
        dto.setStatus(1);
        return dto;
    }

    private SysUser buildUser(Long id, String username, UserRole role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role.name());
        user.setStatus(1);
        return user;
    }
}

package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
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

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("newNick");
        request.setEmail("new@test.com");
        authService.updateProfile(1L, request);

        verify(sysUserMapper).updateById(any(SysUser.class));
        verify(userCacheService).evict(1L);
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

package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
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
        given(userCacheService.get(1L)).willReturn(cached);

        UserProfileDTO result = authService.getProfile(1L);

        assertThat(result.getUsername()).isEqualTo("alice");
        verifyNoInteractions(sysUserMapper);
    }

    @Test
    @DisplayName("Should_QueryDbAndWriteCache_When_CacheMiss")
    void Should_QueryDbAndWriteCache_When_CacheMiss() {
        SysUser user = buildUser(2L, "bob", UserRole.ADMIN);
        given(userCacheService.get(2L)).willReturn(null);
        given(sysUserMapper.selectById(2L)).willReturn(user);

        UserProfileDTO result = authService.getProfile(2L);

        assertThat(result.getUsername()).isEqualTo("bob");
        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(sysUserMapper).selectById(2L);
        verify(userCacheService).put(eq(2L), any(UserProfileDTO.class));
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

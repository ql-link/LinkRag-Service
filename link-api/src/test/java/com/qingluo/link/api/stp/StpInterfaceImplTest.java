package com.qingluo.link.api.stp;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private UserCacheService userCacheService;

    @InjectMocks
    private StpInterfaceImpl stpInterface;

    @Test
    @DisplayName("Should_ReturnRoleFromCache_When_CacheHit")
    void Should_ReturnRoleFromCache_When_CacheHit() {
        UserProfileDTO cached = buildDto(1L, UserRole.ADMIN);
        given(userCacheService.get(1L)).willReturn(cached);

        List<String> roles = stpInterface.getRoleList(1L, "login");

        assertThat(roles).containsExactly("ADMIN");
        verifyNoInteractions(sysUserMapper);
    }

    @Test
    @DisplayName("Should_QueryDbAndWriteCache_When_CacheMiss")
    void Should_QueryDbAndWriteCache_When_CacheMiss() {
        SysUser user = buildUser(2L, UserRole.USER);
        given(userCacheService.get(2L)).willReturn(null);
        given(sysUserMapper.selectById(2L)).willReturn(user);

        List<String> roles = stpInterface.getRoleList(2L, "login");

        assertThat(roles).containsExactly("USER");
        verify(sysUserMapper).selectById(2L);
        verify(userCacheService).put(eq(2L), any(UserProfileDTO.class));
    }

    @Test
    @DisplayName("Should_ReturnEmpty_When_CacheMissAndUserNotFound")
    void Should_ReturnEmpty_When_CacheMissAndUserNotFound() {
        given(userCacheService.get(99L)).willReturn(null);
        given(sysUserMapper.selectById(99L)).willReturn(null);

        List<String> roles = stpInterface.getRoleList(99L, "login");

        assertThat(roles).isEmpty();
        verify(userCacheService, never()).put(any(), any());
    }

    @Test
    @DisplayName("Should_ReturnEmptyPermissions_Always")
    void Should_ReturnEmptyPermissions_Always() {
        List<String> permissions = stpInterface.getPermissionList(1L, "login");

        assertThat(permissions).isEmpty();
    }

    private UserProfileDTO buildDto(Long id, UserRole role) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(id);
        dto.setRole(role.name());
        return dto;
    }

    private SysUser buildUser(Long id, UserRole role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role.name());
        return user;
    }
}

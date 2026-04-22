package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.request.UpdateUserRoleRequest;
import com.qingluo.link.model.dto.request.UpdateUserStatusRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.model.enums.UserRole;
import com.qingluo.link.service.cache.UserCacheService;
import com.qingluo.link.service.impl.admin.AdminUserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private UserCacheService userCacheService;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    // ---- listUsers ----

    @Test
    @DisplayName("Should_ReturnPageOfUsers_When_ListUsers")
    void Should_ReturnPageOfUsers_When_ListUsers() {
        SysUser user = buildUser(1L, "alice", UserRole.USER);
        IPage<SysUser> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(user));
        mockPage.setTotal(1);
        given(sysUserMapper.selectPage(any(), any())).willReturn(mockPage);

        PageResult<UserProfileDTO> result = adminUserService.listUsers(1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getUsername()).isEqualTo("alice");
    }

    // ---- updateUserStatus ----

    @Test
    @DisplayName("Should_UpdateStatusAndEvictCache_When_UserExists")
    void Should_UpdateStatusAndEvictCache_When_UserExists() {
        SysUser user = buildUser(1L, "alice", UserRole.USER);
        given(sysUserMapper.selectById(1L)).willReturn(user);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(0);
        adminUserService.updateUserStatus(1L, request);

        verify(sysUserMapper).updateById(any(SysUser.class));
        verify(userCacheService).evict(1L);
    }

    @Test
    @DisplayName("Should_ThrowNotFoundException_When_UpdateStatusAndUserNotFound")
    void Should_ThrowNotFoundException_When_UpdateStatusAndUserNotFound() {
        given(sysUserMapper.selectById(99L)).willReturn(null);

        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(0);

        assertThatThrownBy(() -> adminUserService.updateUserStatus(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- updateUserRole ----

    @Test
    @DisplayName("Should_UpdateRoleAndEvictCache_When_UserExists")
    void Should_UpdateRoleAndEvictCache_When_UserExists() {
        SysUser user = buildUser(1L, "alice", UserRole.USER);
        given(sysUserMapper.selectById(1L)).willReturn(user);

        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRole(UserRole.ADMIN.name());
        adminUserService.updateUserRole(1L, request);

        verify(sysUserMapper).updateById(any(SysUser.class));
        verify(userCacheService).evict(1L);
    }

    @Test
    @DisplayName("Should_ThrowNotFoundException_When_UpdateRoleAndUserNotFound")
    void Should_ThrowNotFoundException_When_UpdateRoleAndUserNotFound() {
        given(sysUserMapper.selectById(99L)).willReturn(null);

        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRole(UserRole.ADMIN.name());

        assertThatThrownBy(() -> adminUserService.updateUserRole(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- helper ----

    private SysUser buildUser(Long id, String username, UserRole role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role.name());
        user.setStatus(1);
        return user;
    }
}

package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.model.dto.request.UpdateUserRoleRequest;
import com.qingluo.link.model.dto.request.UpdateUserStatusRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.model.enums.UserRole;
import com.qingluo.link.service.AdminUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminUserService adminUserService;

    @InjectMocks
    private AdminController adminController;

    @Test
    @DisplayName("Should_ReturnPageResult_When_ListUsers")
    void Should_ReturnPageResult_When_ListUsers() {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(1L);
        dto.setUsername("alice");
        PageResult<UserProfileDTO> page = new PageResult<>(List.of(dto), 1, 1, 10);
        given(adminUserService.listUsers(1, 10)).willReturn(page);

        Result<PageResult<UserProfileDTO>> result = adminController.listUsers(1, 10);

        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getItems().get(0).getUsername()).isEqualTo("alice");
        verify(adminUserService).listUsers(1, 10);
    }

    @Test
    @DisplayName("Should_DelegateToService_When_UpdateUserStatus")
    void Should_DelegateToService_When_UpdateUserStatus() {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus(0);

        Result<Void> result = adminController.updateUserStatus(1L, request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(adminUserService).updateUserStatus(1L, request);
    }

    @Test
    @DisplayName("Should_DelegateToService_When_UpdateUserRole")
    void Should_DelegateToService_When_UpdateUserRole() {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setRole(UserRole.ADMIN.name());

        Result<Void> result = adminController.updateUserRole(1L, request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(adminUserService).updateUserRole(1L, request);
    }

    @Test
    @DisplayName("Should_HaveSaCheckRoleAdminAnnotation_On_AdminController")
    void Should_HaveSaCheckRoleAdminAnnotation_On_AdminController() {
        SaCheckRole annotation = AdminController.class.getAnnotation(SaCheckRole.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("ADMIN");
    }
}

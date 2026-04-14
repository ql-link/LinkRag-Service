package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateUserRoleRequest;
import com.qingluo.link.model.dto.request.UpdateUserStatusRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AdminProviderService;
import com.qingluo.link.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员控制器
 * <p>提供用户管理、厂商管理等管理员功能</p>
 *
 * @author qingluo
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/admin")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminProviderService adminProviderService;

    // ---- 用户管理 ----

    /**
     * 分页查询用户列表
     */
    @GetMapping("/users")
    public Result<PageResult<UserProfileDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminUserService.listUsers(page, size));
    }

    /**
     * 修改用户状态（启用/禁用）
     */
    @PatchMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id,
                                         @RequestBody @Validated UpdateUserStatusRequest request) {
        adminUserService.updateUserStatus(id, request);
        return Result.success(null);
    }

    /**
     * 修改用户角色
     */
    @PatchMapping("/users/{id}/role")
    public Result<Void> updateUserRole(@PathVariable Long id,
                                       @RequestBody @Validated UpdateUserRoleRequest request) {
        adminUserService.updateUserRole(id, request);
        return Result.success(null);
    }

    // ---- 系统厂商管理 ----

    /**
     * 分页查询厂商列表
     */
    @GetMapping("/providers")
    public Result<PageResult<SystemProvider>> listProviders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminProviderService.listProviders(page, size));
    }

    /**
     * 创建厂商
     */
    @PostMapping("/providers")
    public Result<Void> createProvider(@RequestBody @Validated CreateProviderRequest request) {
        adminProviderService.createProvider(request);
        return Result.success(null);
    }

    /**
     * 更新厂商
     */
    @PatchMapping("/providers/{id}")
    public Result<Void> updateProvider(@PathVariable Long id,
                                      @RequestBody @Validated UpdateProviderRequest request) {
        adminProviderService.updateProvider(id, request);
        return Result.success(null);
    }

    /**
     * 删除厂商
     */
    @DeleteMapping("/providers/{id}")
    public Result<Void> deleteProvider(@PathVariable Long id) {
        adminProviderService.deleteProvider(id);
        return Result.success(null);
    }

    /**
     * 启用/禁用厂商
     */
    @PatchMapping("/providers/{id}/active")
    public Result<Void> toggleProviderActive(@PathVariable Long id,
                                            @RequestParam boolean isActive) {
        adminProviderService.toggleActive(id, isActive);
        return Result.success(null);
    }
}

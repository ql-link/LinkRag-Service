package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateKnowledgeFileConfigRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateUserRoleRequest;
import com.qingluo.link.model.dto.request.UpdateUserStatusRequest;
import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AdminKnowledgeFileConfigService;
import com.qingluo.link.service.AdminProviderService;
import com.qingluo.link.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "管理员接口", description = "用户管理、LLM厂商管理（需ADMIN角色）")
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminProviderService adminProviderService;
    private final AdminKnowledgeFileConfigService adminKnowledgeFileConfigService;

    // ---- 用户管理 ----

    /**
     * 查询用户列表
     *
     * @param page 页码（默认1）
     * @param size 每页条数（默认10）
     * @return 用户列表（分页）
     */
    @GetMapping("/users")
    @Operation(summary = "查询用户列表", description = "分页查询所有用户列表，按创建时间倒序")
    public Result<PageResult<UserProfileDTO>> listUsers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminUserService.listUsers(page, size));
    }

    /**
     * 修改用户状态
     *
     * @param id      用户ID
     * @param request 状态信息（status: 1=启用, 0=禁用）
     * @return 无返回内容
     */
    @PatchMapping("/users/{id}/status")
    @Operation(summary = "修改用户状态", description = "启用或禁用指定用户，禁用后该用户无法登录")
    public Result<Void> updateUserStatus(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @RequestBody @Validated UpdateUserStatusRequest request) {
        adminUserService.updateUserStatus(id, request);
        return Result.success(null);
    }

    /**
     * 修改用户角色
     *
     * @param id      用户ID
     * @param request 角色信息（role: ADMIN或USER）
     * @return 无返回内容
     */
    @PatchMapping("/users/{id}/role")
    @Operation(summary = "修改用户角色", description = "将普通用户提升为管理员，或降级为普通用户")
    public Result<Void> updateUserRole(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @RequestBody @Validated UpdateUserRoleRequest request) {
        adminUserService.updateUserRole(id, request);
        return Result.success(null);
    }

    // ---- 系统厂商管理 ----

    /**
     * 查询厂商列表
     *
     * @param page 页码（默认1）
     * @param size 每页条数（默认10）
     * @return 厂商列表（分页，按优先级倒序）
     */
    @GetMapping("/providers")
    @Operation(summary = "查询厂商列表", description = "分页查询所有LLM厂商列表，按优先级倒序")
    public Result<PageResult<SystemProvider>> listProviders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminProviderService.listProviders(page, size));
    }

    @GetMapping("/knowledge-file-config")
    @Operation(summary = "查询知识文件上传配置", description = "查询当前生效的知识文件上传大小限制和格式白名单")
    public Result<KnowledgeFileConfigDTO> getKnowledgeFileConfig() {
        return Result.success(adminKnowledgeFileConfigService.getCurrentConfig());
    }

    @PatchMapping("/knowledge-file-config")
    @Operation(summary = "修改知识文件上传配置", description = "修改当前生效的知识文件上传大小限制和格式白名单")
    public Result<Void> updateKnowledgeFileConfig(@RequestBody @Validated UpdateKnowledgeFileConfigRequest request) {
        adminKnowledgeFileConfigService.updateConfig(AuthContext.getLoginUserIdOrThrow(), request);
        return Result.success(null);
    }

    /**
     * 创建厂商
     *
     * @param request 厂商信息
     * @return 无返回内容
     */
    @PostMapping("/providers")
    @Operation(summary = "创建厂商", description = "创建一个新的LLM系统厂商配置")
    public Result<Void> createProvider(@RequestBody @Validated CreateProviderRequest request) {
        adminProviderService.createProvider(request);
        return Result.success(null);
    }

    /**
     * 更新厂商
     *
     * @param id      厂商ID
     * @param request 更新内容
     * @return 无返回内容
     */
    @PatchMapping("/providers/{id}")
    @Operation(summary = "更新厂商", description = "部分更新厂商字段，变更后双删缓存")
    public Result<Void> updateProvider(
            @Parameter(description = "厂商ID") @PathVariable Long id,
            @RequestBody @Validated UpdateProviderRequest request) {
        adminProviderService.updateProvider(id, request);
        return Result.success(null);
    }

    /**
     * 删除厂商
     *
     * @param id 厂商ID
     * @return 无返回内容
     */
    @DeleteMapping("/providers/{id}")
    @Operation(summary = "删除厂商", description = "删除指定的LLM厂商配置")
    public Result<Void> deleteProvider(@Parameter(description = "厂商ID") @PathVariable Long id) {
        adminProviderService.deleteProvider(id);
        return Result.success(null);
    }

    /**
     * 启用/禁用厂商
     *
     * @param id       厂商ID
     * @param isActive 是否启用（true启用，false禁用）
     * @return 无返回内容
     */
    @PatchMapping("/providers/{id}/active")
    @Operation(summary = "启用/禁用厂商", description = "启用或禁用指定的LLM厂商，禁用后用户不可见")
    public Result<Void> toggleProviderActive(
            @Parameter(description = "厂商ID") @PathVariable Long id,
            @Parameter(description = "是否启用") @RequestParam boolean isActive) {
        adminProviderService.toggleActive(id, isActive);
        return Result.success(null);
    }
}

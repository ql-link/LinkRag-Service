package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.AddProviderModelRequest;
import com.qingluo.link.model.dto.request.CreatePresetRequest;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.PublishModelSyncCandidateRequest;
import com.qingluo.link.model.dto.request.SyncProviderModelsRequest;
import com.qingluo.link.model.dto.request.UpdateDocumentFileConfigRequest;
import com.qingluo.link.model.dto.request.UpdateModelSyncCandidateReviewRequest;
import com.qingluo.link.model.dto.request.UpdatePresetRequest;
import com.qingluo.link.model.dto.request.UpdateProviderModelRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateUserRoleRequest;
import com.qingluo.link.model.dto.request.UpdateUserStatusRequest;
import com.qingluo.link.model.dto.entity.ProviderModelSyncCandidate;
import com.qingluo.link.model.dto.entity.ProviderModelSyncJob;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.ProviderIconUploadDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AdminDocumentFileConfigService;
import com.qingluo.link.service.AdminProviderService;
import com.qingluo.link.service.AdminUserService;
import com.qingluo.link.service.OssApplicationService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.ProviderModelSyncService;
import com.qingluo.link.service.SystemPresetService;
import com.qingluo.link.service.oss.UploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    private final AdminDocumentFileConfigService adminDocumentFileConfigService;
    private final ProviderModelService providerModelService;
    private final ProviderModelSyncService providerModelSyncService;
    private final SystemPresetService systemPresetService;
    private final OssApplicationService ossApplicationService;

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

    @GetMapping("/document-file-config")
    @Operation(summary = "查询文档文件上传配置", description = "查询当前生效的文档文件上传大小限制和格式白名单")
    public Result<DocumentFileConfigDTO> getDocumentFileConfig() {
        return Result.success(adminDocumentFileConfigService.getCurrentConfig());
    }

    @PatchMapping("/document-file-config")
    @Operation(summary = "修改文档文件上传配置", description = "修改当前生效的文档文件上传大小限制和格式白名单")
    public Result<Void> updateDocumentFileConfig(@RequestBody @Validated UpdateDocumentFileConfigRequest request) {
        adminDocumentFileConfigService.updateConfig(AuthContext.getLoginUserIdOrThrow(), request);
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

    @PostMapping("/providers/icon")
    @Operation(summary = "上传厂商图标", description = "上传厂商图标到公开 OSS，返回可直接访问的图标 URL 与 OSS object key")
    public Result<ProviderIconUploadDTO> uploadProviderIcon(@RequestParam("file") MultipartFile file) {
        UploadResult uploadResult = ossApplicationService.uploadAndDescribe("providerIcon", file);
        return Result.success(new ProviderIconUploadDTO(uploadResult.previewUrl(), uploadResult.objectKey()));
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

    // ---- 厂商模型能力目录管理 ----

    /**
     * 分页查询厂商模型能力目录，管理端可查看上架与下架项。
     */
    @GetMapping("/provider-models")
    @Operation(summary = "查询厂商模型能力目录", description = "分页查询模型能力目录，支持按厂商、能力、上架状态过滤")
    public Result<PageResult<ProviderModel>> listProviderModels(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "厂商ID") @RequestParam(required = false) Long providerId,
            @Parameter(description = "模型能力") @RequestParam(required = false) String capability,
            @Parameter(description = "是否上架") @RequestParam(required = false) Boolean isActive) {
        return Result.success(providerModelService.listModels(page, size, providerId, capability, isActive));
    }

    /**
     * 向厂商模型能力目录新增一条模型能力（已存在则确保上架）。
     */
    @PostMapping("/providers/{providerId}/models")
    @Operation(summary = "新增厂商模型能力", description = "向某厂商的模型能力目录新增一条 (模型, 能力)")
    public Result<ProviderModel> addProviderModel(
            @Parameter(description = "厂商ID") @PathVariable Long providerId,
            @RequestBody @Validated AddProviderModelRequest request) {
        return Result.success(
                providerModelService.addModelCapability(providerId, request.getModelName(), request.getDisplayName(),
                        request.getCapability(), request.getProtocol(), request.getApiBaseUrl()));
    }

    /**
     * 删除一条厂商模型能力目录项。
     */
    @DeleteMapping("/provider-models/{id}")
    @Operation(summary = "删除厂商模型能力", description = "从模型能力目录删除一条 (模型, 能力)")
    public Result<Void> deleteProviderModel(@Parameter(description = "目录项ID") @PathVariable Long id) {
        providerModelService.deleteModelCapability(id);
        return Result.success(null);
    }

    /**
     * 更新一条厂商模型能力目录项。
     */
    @PatchMapping("/provider-models/{id}")
    @Operation(summary = "更新厂商模型能力", description = "部分更新模型能力目录项字段")
    public Result<ProviderModel> updateProviderModel(
            @Parameter(description = "目录项ID") @PathVariable Long id,
            @RequestBody @Validated UpdateProviderModelRequest request) {
        return Result.success(providerModelService.updateModelCapability(id, request));
    }

    /**
     * 上/下架一条厂商模型能力目录项。
     */
    @PatchMapping("/provider-models/{id}/active")
    @Operation(summary = "上下架厂商模型能力", description = "切换某条模型能力目录项的上架状态")
    public Result<Void> toggleProviderModel(
            @Parameter(description = "目录项ID") @PathVariable Long id,
            @Parameter(description = "是否上架") @RequestParam boolean isActive) {
        providerModelService.toggleModelCapability(id, isActive);
        return Result.success(null);
    }

    // ---- 外部模型目录同步候选管理 ----

    @PostMapping("/providers/{providerId}/model-sync")
    @Operation(summary = "刷新外部模型目录", description = "从外部模型目录源拉取候选项，写入候选表，不直接发布到正式模型目录")
    public Result<ProviderModelSyncJob> refreshProviderModelCandidates(
            @Parameter(description = "厂商ID") @PathVariable Long providerId,
            @RequestBody(required = false) SyncProviderModelsRequest request) {
        String source = request == null ? null : request.getSyncSource();
        return Result.success(providerModelSyncService.refreshProviderModels(providerId, source));
    }

    @GetMapping("/model-sync-jobs")
    @Operation(summary = "查询外部模型目录同步任务", description = "分页查询外部模型目录刷新任务")
    public Result<PageResult<ProviderModelSyncJob>> listProviderModelSyncJobs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "厂商ID") @RequestParam(required = false) Long providerId,
            @Parameter(description = "同步来源") @RequestParam(required = false) String syncSource,
            @Parameter(description = "任务状态") @RequestParam(required = false) String status) {
        return Result.success(providerModelSyncService.listJobs(page, size, providerId, syncSource, status));
    }

    @GetMapping("/model-sync-candidates")
    @Operation(summary = "查询外部模型目录候选", description = "分页查询外部模型候选，管理员审核后可发布到正式模型目录")
    public Result<PageResult<ProviderModelSyncCandidate>> listProviderModelSyncCandidates(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "厂商ID") @RequestParam(required = false) Long providerId,
            @Parameter(description = "同步任务ID") @RequestParam(required = false) Long jobId,
            @Parameter(description = "审核状态") @RequestParam(required = false) String reviewStatus,
            @Parameter(description = "模型能力") @RequestParam(required = false) String capability) {
        return Result.success(
                providerModelSyncService.listCandidates(page, size, providerId, jobId, reviewStatus, capability));
    }

    @PostMapping("/model-sync-candidates/{id}/publish")
    @Operation(summary = "发布外部模型候选", description = "将候选项发布到正式 llm_provider_model 目录；请求体字段可覆盖候选推断值")
    public Result<ProviderModel> publishProviderModelSyncCandidate(
            @Parameter(description = "候选项ID") @PathVariable Long id,
            @RequestBody(required = false) PublishModelSyncCandidateRequest request) {
        PublishModelSyncCandidateRequest actualRequest =
                request == null ? new PublishModelSyncCandidateRequest() : request;
        return Result.success(providerModelSyncService.publishCandidate(id, actualRequest));
    }

    @PatchMapping("/model-sync-candidates/{id}/review")
    @Operation(summary = "更新外部模型候选审核状态", description = "将候选项标记为 PENDING 或 REJECTED")
    public Result<ProviderModelSyncCandidate> updateProviderModelSyncCandidateReview(
            @Parameter(description = "候选项ID") @PathVariable Long id,
            @RequestBody @Validated UpdateModelSyncCandidateReviewRequest request) {
        return Result.success(providerModelSyncService.updateReviewStatus(id, request.getReviewStatus()));
    }

    // ---- 系统预设管理 ----

    /**
     * 列出全部系统预设（平台 Key 脱敏返回）。
     */
    @GetMapping("/system-presets")
    @Operation(summary = "查询系统预设列表", description = "列出全部系统预设，平台 Key 脱敏返回")
    public Result<List<SystemPreset>> listSystemPresets() {
        return Result.success(systemPresetService.listPresets());
    }

    /**
     * 新增系统预设（平台 Key 入库前加密）。
     */
    @PostMapping("/system-presets")
    @Operation(summary = "新增系统预设", description = "预配一条 LinkRag 系统兜底配置，支持手动填写模型运行事实或从正式模型目录快捷加入，平台 Key 入库前加密")
    public Result<Void> createSystemPreset(@RequestBody @Validated CreatePresetRequest request) {
        systemPresetService.createPreset(request);
        return Result.success(null);
    }

    /**
     * 更新系统预设。
     */
    @PatchMapping("/system-presets/{id}")
    @Operation(summary = "更新系统预设", description = "部分更新系统预设，支持手动更新模型运行事实或从正式模型目录重新快捷复制；预设始终归属 LinkRag")
    public Result<SystemPreset> updateSystemPreset(
            @Parameter(description = "预设ID") @PathVariable Long id,
            @RequestBody @Validated UpdatePresetRequest request) {
        return Result.success(systemPresetService.updatePreset(id, request));
    }

    /**
     * 启用/禁用系统预设。
     */
    @PatchMapping("/system-presets/{id}/active")
    @Operation(summary = "启用/禁用系统预设", description = "控制该预设是否可作为系统兜底候选；当前默认预设需先指定替代项再禁用")
    public Result<Void> toggleSystemPreset(
            @Parameter(description = "预设ID") @PathVariable Long id,
            @Parameter(description = "是否启用") @RequestParam boolean isActive) {
        systemPresetService.togglePreset(id, isActive);
        return Result.success(null);
    }

    /**
     * 设置某条系统预设为其能力的系统兜底默认。
     */
    @PatchMapping("/system-presets/{id}/default")
    @Operation(summary = "设置系统默认预设", description = "将该预设设为其能力的 LinkRag 系统兜底默认，并解除同能力其他默认")
    public Result<Void> setDefaultSystemPreset(@Parameter(description = "预设ID") @PathVariable Long id) {
        systemPresetService.setDefaultPreset(id);
        return Result.success(null);
    }

    /**
     * 删除系统预设。
     */
    @DeleteMapping("/system-presets/{id}")
    @Operation(summary = "删除系统预设", description = "删除一条系统预设")
    public Result<Void> deleteSystemPreset(@Parameter(description = "预设ID") @PathVariable Long id) {
        systemPresetService.deletePreset(id);
        return Result.success(null);
    }
}

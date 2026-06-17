package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.SelectEffectiveModelRequest;
import com.qingluo.link.model.dto.request.SetupProviderRequest;
import com.qingluo.link.model.dto.request.ToggleModelRequest;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.service.UserLLMConfigService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * LLM配置控制器
 *
 * <p>用户 LLM 配置两步流：①配置厂商（选厂商 + 填厂商级 Key，自动展开该厂商全部模型能力）；
 * ②按能力选生效模型。模型启停为独立动作。系统预设只读、仅可按能力切换生效。</p>
 *
 * @author qingluo
 */
@RestController
@RequestMapping("/api/v1/llm/configs")
@RequiredArgsConstructor
@Tag(name = "LLM配置接口", description = "用户LLM配置：配置厂商、模型启停、按能力选生效、系统预设")
public class ConfigController {

    private final UserLLMConfigService userLLMConfigService;

    /**
     * 获取用户LLM配置列表（含系统预设行）。
     */
    @GetMapping
    @SaCheckLogin
    @Operation(summary = "获取LLM配置列表", description = "获取当前用户的全部 LLM 配置，包含已禁用与系统预设，Key 脱敏")
    public Result<List<UserLLMConfigDTO>> getConfigs(
            @Parameter(description = "厂商类型，如openai") @RequestParam(required = false) String providerType,
            @Parameter(description = "模型能力，如CHAT/EMBEDDING/SPARSE_EMBEDDING") @RequestParam(required = false) String capability,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean isActive) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(userLLMConfigService.getConfigs(userId, providerType, capability, isActive));
    }

    /**
     * 配置厂商（第一步）：选厂商 + 填厂商级 Key，自动展开该厂商全部模型能力。
     */
    @PostMapping("/setup-provider")
    @SaCheckLogin
    @Operation(summary = "配置厂商", description = "选厂商并填厂商级 Key，系统自动加载该厂商全部模型；重复配置同厂商更新其 Key")
    public Result<List<UserLLMConfigDTO>> setupProvider(@Valid @RequestBody SetupProviderRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(userLLMConfigService.setupProvider(userId, request));
    }

    /**
     * 模型启停（独立窗口）：按厂商 + 模型批量启停该模型全部能力行。
     */
    @PatchMapping("/toggle-model")
    @SaCheckLogin
    @Operation(summary = "模型启停", description = "按厂商+模型批量启停该模型全部能力；关闭后按能力选生效时不再展示")
    public Result<Void> toggleModel(@Valid @RequestBody ToggleModelRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.toggleModel(userId, request);
        return Result.ok(null);
    }

    /**
     * 按能力选生效模型（第二步）：为某能力选定一个启用模型生效，自动解除原生效。
     */
    @PutMapping("/effective")
    @SaCheckLogin
    @Operation(summary = "按能力选生效模型", description = "为某能力选定一个启用模型生效，单用户单能力生效唯一")
    public Result<Void> selectEffectiveModel(@Valid @RequestBody SelectEffectiveModelRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.selectEffectiveModel(userId, request);
        return Result.ok(null);
    }

    /**
     * 查询某能力的生效配置。
     */
    @GetMapping("/default")
    @SaCheckLogin
    @Operation(summary = "查询某能力生效配置", description = "按能力查询当前用户的生效 LLM 配置")
    public Result<UserLLMConfigDTO> getDefaultConfig(
            @Parameter(description = "模型能力，如CHAT/EMBEDDING/SPARSE_EMBEDDING") @RequestParam String capability) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(userLLMConfigService.getDefaultConfig(userId, capability));
    }

    /**
     * 设置某能力生效配置（按配置 ID，含切换到/切回系统预设）。
     */
    @PatchMapping("/{id}/default")
    @SaCheckLogin
    @Operation(summary = "设置某能力生效配置", description = "将当前用户的一条配置设为该能力生效，支持切换到或切回系统预设")
    public Result<Void> setDefaultConfig(
            @Parameter(description = "配置ID") @PathVariable Long id,
            @Parameter(description = "模型能力，如CHAT/EMBEDDING/SPARSE_EMBEDDING") @RequestParam String capability) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.setDefaultConfig(userId, id, capability);
        return Result.ok(null);
    }

    /**
     * 删除用户LLM配置；系统预设只读不可删。
     */
    @DeleteMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "删除LLM配置", description = "删除指定的 LLM 配置；系统预设为只读，删除被拒")
    public Result<Void> deleteConfig(@Parameter(description = "配置ID") @PathVariable Long id) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.deleteConfig(userId, id);
        return Result.ok(null);
    }
}

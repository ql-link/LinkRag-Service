package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.EmergencyDisableLLMConfigRequest;
import com.qingluo.link.model.dto.request.SetCapabilityDefaultRequest;
import com.qingluo.link.model.dto.request.SetupProviderRequest;
import com.qingluo.link.model.dto.request.UpdateLLMConfigActiveRequest;
import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import com.qingluo.link.model.dto.response.ExecutableLLMConfigDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.enums.LLMConfigMutationMode;
import com.qingluo.link.service.LLMCapabilityDefaultService;
import com.qingluo.link.service.LLMModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 LLM 配置与能力默认关系入口。
 */
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
@Tag(name = "LLM配置接口", description = "统一配置身份、厂商凭据、启停与能力默认选择")
public class ConfigController {

    private final LLMModelConfigService configService;
    private final LLMCapabilityDefaultService defaultService;

    @GetMapping("/configs")
    @SaCheckLogin
    @Operation(summary = "获取统一LLM配置列表", description = "返回当前用户配置和各能力当前平台默认配置，唯一身份字段为configId")
    public Result<List<ExecutableLLMConfigDTO>> getConfigs(
        @Parameter(description = "厂商类型") @RequestParam(required = false) String providerType,
        @Parameter(description = "模型能力") @RequestParam(required = false) String capability,
        @Parameter(description = "启用状态") @RequestParam(required = false) Boolean isActive) {
        return Result.success(configService.listVisibleConfigs(
            AuthContext.getLoginUserIdOrThrow(), providerType, capability, isActive));
    }

    @PostMapping("/configs/setup-provider")
    @SaCheckLogin
    @Operation(summary = "配置或刷新厂商", description = "按正式目录创建或刷新用户运行快照，已有自然键复用configId")
    public Result<List<ExecutableLLMConfigDTO>> setupProvider(
        @Valid @RequestBody SetupProviderRequest request) {
        return Result.success(configService.setupProvider(AuthContext.getLoginUserIdOrThrow(), request));
    }

    @PatchMapping("/configs/{configId}/active")
    @SaCheckLogin
    @Operation(summary = "更新用户配置启用状态", description = "标准停用会保护数据集引用并清除该配置的用户默认关系")
    public Result<Void> updateActive(
        @Parameter(description = "全局配置ID") @PathVariable Long configId,
        @Valid @RequestBody UpdateLLMConfigActiveRequest request) {
        configService.changeActive(AuthContext.getLoginUserIdOrThrow(), false, configId,
            request.getIsActive(), LLMConfigMutationMode.STANDARD, null, false);
        return Result.ok(null);
    }

    @PostMapping("/configs/{configId}/emergency-disable")
    @SaCheckLogin
    @Operation(summary = "紧急停用用户配置", description = "保留数据集绑定，所有者必须明确确认，后续精确执行返回配置已停用")
    public Result<Void> emergencyDisable(
        @Parameter(description = "全局配置ID") @PathVariable Long configId,
        @Valid @RequestBody EmergencyDisableLLMConfigRequest request) {
        configService.changeActive(AuthContext.getLoginUserIdOrThrow(), false, configId, false,
            LLMConfigMutationMode.EMERGENCY, null, Boolean.TRUE.equals(request.getConfirmed()));
        return Result.ok(null);
    }

    @DeleteMapping("/configs/{configId}")
    @SaCheckLogin
    @Operation(summary = "删除用户配置", description = "存在数据集引用时拒绝；用户默认关系在同一事务清除")
    public Result<Void> deleteConfig(
        @Parameter(description = "全局配置ID") @PathVariable Long configId) {
        configService.deleteConfig(AuthContext.getLoginUserIdOrThrow(), false, configId);
        return Result.ok(null);
    }

    @GetMapping("/defaults")
    @SaCheckLogin
    @Operation(summary = "查询全部能力默认关系", description = "分别返回用户覆盖、平台默认和当前有效configId")
    public Result<List<CapabilityDefaultDTO>> listDefaults() {
        return Result.success(defaultService.listDefaults(AuthContext.getLoginUserIdOrThrow()));
    }

    @GetMapping("/defaults/{capability}")
    @SaCheckLogin
    @Operation(summary = "查询能力默认关系")
    public Result<CapabilityDefaultDTO> getDefault(
        @Parameter(description = "模型能力") @PathVariable String capability) {
        return Result.success(defaultService.getEffectiveDefault(
            AuthContext.getLoginUserIdOrThrow(), capability));
    }

    @PutMapping("/defaults/{capability}")
    @SaCheckLogin
    @Operation(summary = "设置用户能力默认", description = "只能选择当前用户拥有、启用且能力匹配的USER配置")
    public Result<CapabilityDefaultDTO> setDefault(
        @Parameter(description = "模型能力") @PathVariable String capability,
        @Valid @RequestBody SetCapabilityDefaultRequest request) {
        return Result.success(defaultService.setUserDefault(
            AuthContext.getLoginUserIdOrThrow(), capability, request.getConfigId()));
    }

    @DeleteMapping("/defaults/{capability}")
    @SaCheckLogin
    @Operation(summary = "清除用户能力默认", description = "清除覆盖后恢复跟随平台默认")
    public Result<CapabilityDefaultDTO> clearDefault(
        @Parameter(description = "模型能力") @PathVariable String capability) {
        return Result.success(defaultService.clearUserDefault(
            AuthContext.getLoginUserIdOrThrow(), capability));
    }
}

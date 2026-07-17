package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.AdminPlatformConfigSaveRequest;
import com.qingluo.link.model.dto.request.EmergencyDisableLLMConfigRequest;
import com.qingluo.link.model.dto.request.SetCapabilityDefaultRequest;
import com.qingluo.link.model.dto.request.UpdateLLMConfigActiveRequest;
import com.qingluo.link.model.dto.response.AdminPlatformConfigSaveResult;
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

@RestController
@RequestMapping("/api/v1/admin/llm")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
@Tag(name = "管理员LLM配置接口", description = "维护SYSTEM运行配置与平台能力默认关系")
public class AdminLLMConfigController {

    private final LLMModelConfigService configService;
    private final LLMCapabilityDefaultService defaultService;

    @GetMapping("/configs")
    @Operation(summary = "查询平台LLM配置")
    public Result<List<ExecutableLLMConfigDTO>> listConfigs(
        @Parameter(description = "模型能力") @RequestParam(required = false) String capability,
        @Parameter(description = "启用状态") @RequestParam(required = false) Boolean isActive) {
        return Result.success(configService.listSystemConfigs(capability, isActive));
    }

    @PostMapping("/configs")
    @Operation(summary = "创建平台LLM配置", description = "目录事实、运行配置和可选默认关系在一个业务事务中保存")
    public Result<AdminPlatformConfigSaveResult> createConfig(
        @Valid @RequestBody AdminPlatformConfigSaveRequest request) {
        return Result.success(configService.saveSystemConfig(null, request));
    }

    @PutMapping("/configs/{configId}")
    @Operation(summary = "更新平台LLM配置", description = "保持同一configId并递增运行快照版本")
    public Result<AdminPlatformConfigSaveResult> updateConfig(
        @Parameter(description = "全局配置ID") @PathVariable Long configId,
        @Valid @RequestBody AdminPlatformConfigSaveRequest request) {
        return Result.success(configService.saveSystemConfig(configId, request));
    }

    @PatchMapping("/configs/{configId}/active")
    @Operation(summary = "更新平台配置启用状态")
    public Result<Void> updateActive(
        @Parameter(description = "全局配置ID") @PathVariable Long configId,
        @Valid @RequestBody UpdateLLMConfigActiveRequest request) {
        configService.changeActive(AuthContext.getLoginUserIdOrThrow(), true, configId,
            request.getIsActive(), LLMConfigMutationMode.STANDARD, null, false);
        return Result.ok(null);
    }

    @PostMapping("/configs/{configId}/emergency-disable")
    @Operation(summary = "紧急停用平台配置", description = "当前平台默认被停用时必须同事务指定同能力替代配置")
    public Result<Void> emergencyDisable(
        @Parameter(description = "全局配置ID") @PathVariable Long configId,
        @Valid @RequestBody EmergencyDisableLLMConfigRequest request) {
        configService.changeActive(AuthContext.getLoginUserIdOrThrow(), true, configId, false,
            LLMConfigMutationMode.EMERGENCY, request.getReplacementConfigId(), true);
        return Result.ok(null);
    }

    @DeleteMapping("/configs/{configId}")
    @Operation(summary = "删除平台配置", description = "平台默认或数据集仍引用时拒绝")
    public Result<Void> deleteConfig(
        @Parameter(description = "全局配置ID") @PathVariable Long configId) {
        configService.deleteConfig(AuthContext.getLoginUserIdOrThrow(), true, configId);
        return Result.ok(null);
    }

    @PutMapping("/defaults/{capability}")
    @Operation(summary = "切换平台能力默认", description = "只切换独立默认指针，不改变旧配置状态或数据集绑定")
    public Result<CapabilityDefaultDTO> setDefault(
        @Parameter(description = "模型能力") @PathVariable String capability,
        @Valid @RequestBody SetCapabilityDefaultRequest request) {
        return Result.success(defaultService.setSystemDefault(capability, request.getConfigId()));
    }
}

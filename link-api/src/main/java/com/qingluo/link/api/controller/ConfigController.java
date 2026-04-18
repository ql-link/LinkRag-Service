package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
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
 * <p>提供用户 LLM 配置的创建、查询、修改、删除功能</p>
 *
 * @author qingluo
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/llm/configs")
@RequiredArgsConstructor
@Tag(name = "LLM配置接口", description = "用户LLM配置的增删改查，支持openai/claude/deepseek/glm/aliyun等厂商")
public class ConfigController {

    private final UserLLMConfigService userLLMConfigService;

    /**
     * 获取用户LLM配置列表
     *
     * @param providerType 厂商类型过滤（可选，如openai）
     * @param isActive     启用状态过滤（可选）
     * @return 配置列表（包含脱敏后的apiKeyMasked）
     */
    @GetMapping
    @SaCheckLogin
    @Operation(summary = "获取LLM配置列表", description = "获取当前用户配置的所有LLM API信息，包含已禁用的配置")
    public Result<List<UserLLMConfigDTO>> getConfigs(
            @Parameter(description = "厂商类型，如openai") @RequestParam(required = false) String providerType,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean isActive) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(userLLMConfigService.getConfigs(userId, providerType, isActive));
    }

    /**
     * 创建用户LLM配置
     *
     * @param request 配置信息（providerType, apiKey, modelName等）
     * @return 创建的配置信息（包含脱敏后的apiKeyMasked）
     */
    @PostMapping
    @SaCheckLogin
    @Operation(summary = "创建LLM配置", description = "新增一个LLM API配置，API Key会自动加密存储")
    public Result<UserLLMConfigDTO> createConfig(
            @Valid @RequestBody CreateConfigRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(userLLMConfigService.createConfig(userId, request));
    }

    /**
     * 更新用户LLM配置
     *
     * @param id      配置ID
     * @param request 更新内容（apiKey, priority, isActive等）
     * @return 无返回内容
     */
    @PatchMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "更新LLM配置", description = "部分更新配置字段，支持修改API Key（会重新加密）")
    public Result<Void> updateConfig(
            @Parameter(description = "配置ID") @PathVariable Long id,
            @Valid @RequestBody UpdateConfigRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.updateConfig(userId, id, request);
        return Result.ok(null);
    }

    /**
     * 删除用户LLM配置
     *
     * @param id 配置ID
     * @return 无返回内容
     */
    @DeleteMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "删除LLM配置", description = "删除指定的LLM配置，删除后影响新对话使用")
    public Result<Void> deleteConfig(@Parameter(description = "配置ID") @PathVariable Long id) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.deleteConfig(userId, id);
        return Result.ok(null);
    }
}

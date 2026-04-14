package com.qingluo.link.api.controller;

import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.service.UserLLMConfigService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/llm/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final UserLLMConfigService userLLMConfigService;

    @GetMapping
    @SaCheckLogin
    public Result<List<UserLLMConfigDTO>> getConfigs(
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) Boolean isActive) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(userLLMConfigService.getConfigs(userId, providerType, isActive));
    }

    @PostMapping
    @SaCheckLogin
    public Result<UserLLMConfigDTO> createConfig(
            @Valid @RequestBody CreateConfigRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        return Result.success(userLLMConfigService.createConfig(userId, request));
    }

    @PatchMapping("/{id}")
    @SaCheckLogin
    public Result<Void> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateConfigRequest request) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.updateConfig(userId, id, request);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @SaCheckLogin
    public Result<Void> deleteConfig(@PathVariable Long id) {
        Long userId = AuthContext.getLoginUserIdOrThrow();
        userLLMConfigService.deleteConfig(userId, id);
        return Result.ok(null);
    }
}

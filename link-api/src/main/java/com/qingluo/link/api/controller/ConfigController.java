package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/llm/configs")
@RequiredArgsConstructor
public class ConfigController {

    @GetMapping
    @SaCheckLogin
    public Result<List<UserLLMConfigDTO>> getConfigs(
            @RequestParam(required = false) String providerType,
            @RequestParam(required = false) Boolean isActive) {
        // TODO: 调用 UserLLMConfigService.getConfigs()
        return Result.success(List.of());
    }

    @PostMapping
    @SaCheckLogin
    public Result<UserLLMConfigDTO> createConfig(
            @Valid @RequestBody CreateConfigRequest request) {
        // TODO: 调用 UserLLMConfigService.createConfig()
        return Result.success(new UserLLMConfigDTO());
    }

    @PatchMapping("/{id}")
    @SaCheckLogin
    public Result<Void> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody UpdateConfigRequest request) {
        // TODO: 调用 UserLLMConfigService.updateConfig()
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    @SaCheckLogin
    public Result<Void> deleteConfig(@PathVariable Long id) {
        // TODO: 调用 UserLLMConfigService.deleteConfig()
        return Result.ok(null);
    }
}
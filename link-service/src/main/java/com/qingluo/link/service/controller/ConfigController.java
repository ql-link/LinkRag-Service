package com.qingluo.link.service.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.core.dto.request.CreateConfigRequest;
import com.qingluo.link.core.dto.request.UpdateConfigRequest;
import com.qingluo.link.core.dto.response.Result;
import com.qingluo.link.core.dto.response.UserLLMConfigDTO;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.service.UserLLMConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 用户 LLM 配置控制器
 */
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class ConfigController {

    private final UserLLMConfigService userLLMConfigService;

    @SaCheckLogin
    @GetMapping("/configs")
    public Result<List<UserLLMConfigDTO>> listConfigs() {
        String userId = AuthContext.getCurrentUserId();
        List<UserLLMConfigDTO> result = userLLMConfigService.listUserConfigs(userId);
        return Result.success(result);
    }

    @SaCheckLogin
    @GetMapping("/configs/{id}")
    public Result<UserLLMConfigDTO> getConfig(@PathVariable String id) {
        String userId = AuthContext.getCurrentUserId();
        UserLLMConfigDTO result = userLLMConfigService.getUserConfig(userId, id);
        return Result.success(result);
    }

    @SaCheckLogin
    @PostMapping("/configs")
    public Result<UserLLMConfigDTO> createConfig(@Valid @RequestBody CreateConfigRequest request) {
        String userId = AuthContext.getCurrentUserId();
        UserLLMConfigDTO result = userLLMConfigService.createUserConfig(userId, request);
        return Result.created(result);
    }

    @SaCheckLogin
    @PatchMapping("/configs/{id}")
    public Result<Void> updateConfig(
            @PathVariable String id,
            @RequestBody UpdateConfigRequest request) {
        String userId = AuthContext.getCurrentUserId();
        userLLMConfigService.updateUserConfig(userId, id, request);
        return Result.success(null);
    }

    @SaCheckLogin
    @DeleteMapping("/configs/{id}")
    public Result<Void> deleteConfig(@PathVariable String id) {
        String userId = AuthContext.getCurrentUserId();
        userLLMConfigService.deleteUserConfig(userId, id);
        return Result.success(null);
    }
}
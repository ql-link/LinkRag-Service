package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.model.dto.request.FetchProviderModelsRequest;
import com.qingluo.link.model.dto.response.ProviderDTO;
import com.qingluo.link.model.dto.response.ProviderModelListDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.SystemProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 用户侧 LLM 厂商控制器。
 *
 * <p>面向普通登录用户展示可添加的启用厂商与模型能力，不暴露管理端配置细节。</p>
 */
@RestController
@RequestMapping("/api/v1/llm/providers")
@RequiredArgsConstructor
@Tag(name = "LLM厂商接口", description = "用户侧查询可用LLM厂商并临时拉取模型列表")
public class ProviderController {

    private final SystemProviderService systemProviderService;

    /**
     * 查询用户可选择的启用厂商。
     *
     * @param capability 能力过滤条件
     * @return 启用厂商列表
     */
    @GetMapping
    @SaCheckLogin
    @Operation(summary = "查询可用LLM厂商", description = "用户添加配置前按能力查询启用中的厂商，不返回系统模型目录")
    public Result<List<ProviderDTO>> getProviders(
            @Parameter(description = "模型能力，如CHAT/OCR/EMBEDDING") @RequestParam String capability) {
        return Result.success(systemProviderService.getActiveProvidersByCapability(capability));
    }

    @PostMapping("/{providerId}/models")
    @SaCheckLogin
    @Operation(summary = "临时拉取厂商模型列表", description = "使用用户提供的 API Key 通过 Java 后端代理拉取模型列表，结果不落库")
    public Result<ProviderModelListDTO> fetchModels(
            @Parameter(description = "厂商ID") @PathVariable Long providerId,
            @Valid @RequestBody FetchProviderModelsRequest request) {
        return Result.success(systemProviderService.fetchProviderModels(providerId, request));
    }
}

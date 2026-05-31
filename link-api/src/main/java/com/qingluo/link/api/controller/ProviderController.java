package com.qingluo.link.api.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.qingluo.link.model.dto.response.ProviderModelDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.SystemProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户侧 LLM 厂商模型控制器。
 *
 * <p>面向普通登录用户展示可添加的启用厂商与模型能力，不暴露管理端配置细节。</p>
 */
@RestController
@RequestMapping("/api/v1/llm/providers")
@RequiredArgsConstructor
@Tag(name = "LLM厂商模型接口", description = "用户侧查询可用LLM厂商和模型")
public class ProviderController {

    private final SystemProviderService systemProviderService;

    /**
     * 查询用户可选择的启用厂商和模型。
     *
     * @param capability 能力过滤条件，可为空
     * @return 启用厂商及其可选模型能力列表
     */
    @GetMapping
    @SaCheckLogin
    @Operation(summary = "查询可用LLM厂商和模型", description = "用户添加配置前查询启用中的厂商和模型，支持按能力过滤")
    public Result<List<ProviderModelDTO>> getProviders(
            @Parameter(description = "模型能力，如CHAT/OCR/EMBEDDING") @RequestParam(required = false) String capability) {
        return Result.success(systemProviderService.getActiveProviderModels(capability));
    }
}

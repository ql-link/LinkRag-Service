package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 新增厂商模型能力请求（管理端）
 *
 * <p>向某厂商的模型能力目录新增一条 (模型, 能力)；一个模型多能力分多次新增。</p>
 */
@Data
@Schema(description = "新增厂商模型能力请求")
public class AddProviderModelRequest {

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称", example = "gpt-4o-realtime")
    private String modelName;

    @NotBlank(message = "能力标识不能为空")
    @Schema(description = "模型能力，如 CHAT/OCR/EMBEDDING", example = "CHAT")
    private String capability;

    @NotBlank(message = "协议不能为空")
    @Schema(description = "调用协议，如 openai/anthropic/google/jina/dashscope", example = "openai")
    private String protocol;

    @NotBlank(message = "调用入口不能为空")
    @Schema(description = "调用入口基地址（不含 capability 后缀）", example = "https://api.openai.com/v1")
    private String apiBaseUrl;
}

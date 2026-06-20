package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新厂商模型能力请求（管理端）。
 */
@Data
@Schema(description = "更新厂商模型能力请求")
public class UpdateProviderModelRequest {

    @Schema(description = "模型名称", example = "gpt-4o")
    private String modelName;

    @Schema(description = "能力标识，如 CHAT/EMBEDDING/SPARSE_EMBEDDING", example = "CHAT")
    private String capability;

    @Schema(description = "调用协议，如 openai/anthropic/google/jina/dashscope", example = "openai")
    private String protocol;

    @Schema(description = "调用入口完整端点 URL（google 等协议例外可存协议基地址）", example = "https://api.openai.com/v1/chat/completions")
    private String apiBaseUrl;

    @Schema(description = "是否上架", example = "true")
    private Boolean isActive;
}

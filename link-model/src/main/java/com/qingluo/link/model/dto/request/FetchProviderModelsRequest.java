package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 拉取厂商模型列表请求。
 */
@Data
@Schema(description = "拉取厂商模型列表请求")
public class FetchProviderModelsRequest {

    @NotBlank(message = "API Key 不能为空")
    @Schema(description = "API Key，仅用于本次模型列表请求，不入库", example = "sk-xxxxx")
    private String apiKey;

    @Schema(description = "自定义 API 地址，可选", example = "https://api.openai.com/v1")
    private String customApiBaseUrl;
}

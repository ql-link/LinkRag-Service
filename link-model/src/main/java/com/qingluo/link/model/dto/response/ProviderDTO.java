package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户侧可选 LLM 厂商信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户侧可选 LLM 厂商信息")
public class ProviderDTO {

    @Schema(description = "厂商ID", example = "10000")
    private Long providerId;

    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @Schema(description = "默认 API 地址", example = "https://api.openai.com/v1")
    private String apiBaseUrl;

    @Schema(description = "支持的能力列表", example = "[\"CHAT\",\"EMBEDDING\"]")
    private List<String> supportedCapabilities;
}

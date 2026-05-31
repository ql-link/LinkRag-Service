package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户侧可选厂商模型 DTO。
 *
 * <p>只暴露启用厂商及其可选模型能力，不包含系统内部配置 schema 或密钥信息。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户侧可选厂商模型信息")
public class ProviderModelDTO {

    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @Schema(description = "厂商名称", example = "OpenAI")
    private String providerName;

    @Schema(description = "可选模型列表")
    private List<ModelCapabilityDTO> models;
}

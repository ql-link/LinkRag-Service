package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前实际生效的 LLM 配置。
 *
 * <p>source + configId 共同定位运行配置：USER 指向 llm_user_config，SYSTEM 指向
 * llm_system_preset。对外只返回脱敏 Key，Python 执行端可按同一对引用读取对应表。</p>
 */
@Data
@Schema(description = "当前实际生效的LLM配置")
public class EffectiveLLMConfigDTO {

    @Schema(description = "配置来源：USER=用户自配，SYSTEM=LinkRag系统兜底", example = "SYSTEM")
    private String source;

    @Schema(description = "配置ID；source=USER时为llm_user_config.id，source=SYSTEM时为llm_system_preset.id", example = "10001")
    private Long configId;

    @Schema(description = "厂商ID", example = "10001")
    private Long providerId;

    @Schema(description = "厂商类型", example = "linkrag")
    private String providerType;

    @Schema(description = "模型名称", example = "deepseek-v3")
    private String modelName;

    @Schema(description = "模型展示名；为空时等于模型名称", example = "DeepSeek V4 Flash")
    private String displayName;

    @Schema(description = "模型能力", example = "CHAT")
    private String capability;

    @Schema(description = "调用协议", example = "openai")
    private String protocol;

    @Schema(description = "API地址（复制自模型能力层事实）")
    private String apiBaseUrl;

    @Schema(description = "API Key(脱敏)")
    private String apiKeyMasked;
}

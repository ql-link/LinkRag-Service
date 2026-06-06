package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 配置厂商请求（两步配置·第一步）
 *
 * <p>用户只选厂商一个维度并填厂商级 Key，系统据此从模型能力目录展开该厂商全部
 * (模型, 能力) 写入用户配置表。Key 为厂商级，该厂商下所有模型共用。</p>
 */
@Data
@Schema(description = "配置厂商请求")
public class SetupProviderRequest {

    @NotBlank(message = "厂商类型不能为空")
    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @NotBlank(message = "API Key 不能为空")
    @Schema(description = "厂商级 API Key", example = "sk-xxxxx")
    private String apiKey;
}

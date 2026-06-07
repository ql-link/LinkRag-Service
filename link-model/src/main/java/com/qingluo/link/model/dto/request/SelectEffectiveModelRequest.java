package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 按能力选生效模型请求（两步配置·第二步）
 *
 * <p>为某个能力选定一个已启用模型作为生效配置；设新生效前自动解除该能力原生效，
 * 保证单用户单能力生效唯一。</p>
 */
@Data
@Schema(description = "按能力选生效模型请求")
public class SelectEffectiveModelRequest {

    @NotBlank(message = "能力标识不能为空")
    @Schema(description = "模型能力，如 CHAT/OCR/EMBEDDING", example = "CHAT")
    private String capability;

    @NotBlank(message = "厂商类型不能为空")
    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称", example = "gpt-4o")
    private String modelName;
}

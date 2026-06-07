package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 模型启停请求（独立窗口，与配置厂商解耦）
 *
 * <p>按 (厂商, 模型) 批量切换该模型全部能力行的启用状态；关闭后该模型在
 * 「按能力选生效模型」时不再展示。</p>
 */
@Data
@Schema(description = "模型启停请求")
public class ToggleModelRequest {

    @NotBlank(message = "厂商类型不能为空")
    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称", example = "gpt-4o-mini")
    private String modelName;

    @NotNull(message = "启停状态不能为空")
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;
}

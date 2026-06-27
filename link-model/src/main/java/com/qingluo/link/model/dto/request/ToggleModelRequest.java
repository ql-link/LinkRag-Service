package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 模型启停请求（独立窗口，与配置厂商解耦）
 *
 * <p>capability 为空时按 (厂商, 模型) 批量切换该模型全部能力行；capability 存在时
 * 只切换该模型的指定能力行。</p>
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

    @Schema(description = "模型能力；为空时兼容旧前端，批量启停该模型全部能力", example = "VISION")
    private String capability;

    @NotNull(message = "启停状态不能为空")
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;
}

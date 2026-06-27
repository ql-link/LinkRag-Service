package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建系统预设请求（管理端）
 *
 * <p>预配一条系统兜底配置：指定厂商下某模型的某能力，并附平台 Key（入库前加密）。
 * 当用户没有自配生效模型时，Java 回退到对应能力的 active + default 系统预设。</p>
 */
@Data
@Schema(description = "创建系统预设请求")
public class CreatePresetRequest {

    @NotNull(message = "厂商ID不能为空")
    @Schema(description = "厂商ID", example = "1")
    private Long providerId;

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称", example = "deepseek-v3")
    private String modelName;

    @NotBlank(message = "能力标识不能为空")
    @Schema(description = "模型能力，如 CHAT/EMBEDDING/SPARSE_EMBEDDING", example = "CHAT")
    private String capability;

    @NotBlank(message = "平台 Key 不能为空")
    @Schema(description = "平台 Key（明文，入库前加密）", example = "sk-platform-xxxxx")
    private String apiKey;

    @Schema(description = "是否设为该能力的系统兜底默认配置", example = "false")
    private Boolean isDefault;
}

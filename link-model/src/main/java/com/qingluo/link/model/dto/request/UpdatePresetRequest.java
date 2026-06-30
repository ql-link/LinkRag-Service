package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新系统预设请求（管理端）。
 */
@Data
@Schema(description = "更新系统预设请求")
public class UpdatePresetRequest {

    @Schema(description = "厂商ID", example = "1")
    private Long providerId;

    @Schema(description = "模型名称", example = "deepseek-v3")
    private String modelName;

    @Schema(description = "能力标识，如 CHAT/EMBEDDING/SPARSE_EMBEDDING", example = "CHAT")
    private String capability;

    @Schema(description = "平台 Key（明文，入库前加密）", example = "sk-platform-xxxxx")
    private String apiKey;

    @Schema(description = "是否启用为系统兜底候选", example = "true")
    private Boolean isActive;

    @Schema(description = "是否设为该能力的系统兜底默认配置", example = "false")
    private Boolean isDefault;
}

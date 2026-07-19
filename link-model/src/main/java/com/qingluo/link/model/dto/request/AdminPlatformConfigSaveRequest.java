package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理员平台配置原子保存请求")
public class AdminPlatformConfigSaveRequest {

    @Schema(description = "复制既有正式模型目录项的ID")
    @Positive(message = "模型目录项ID必须为正整数")
    private Long sourceProviderModelId;

    @Valid
    @Schema(description = "需与平台配置同事务发布的模型目录事实")
    private CatalogMutation catalogMutation;

    @Schema(description = "平台API Key；创建时必填，更新时为空表示保留原密钥")
    @Size(max = 512, message = "API Key长度不能超过512")
    private String apiKey;

    @Schema(description = "是否在同一事务设为该能力的SYSTEM默认", example = "false")
    private Boolean setAsDefault = false;

    @Data
    @Schema(description = "模型目录原子变更")
    public static class CatalogMutation {

        @Schema(description = "厂商ID", example = "10000")
        @NotNull(message = "厂商ID不能为空")
        @Positive(message = "厂商ID必须为正整数")
        private Long providerId;

        @Schema(description = "模型名称", example = "gpt-4o")
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 128, message = "模型名称长度不能超过128")
        private String modelName;

        @Schema(description = "展示名", example = "GPT-4o")
        @Size(max = 64, message = "展示名长度不能超过64")
        private String displayName;

        @Schema(description = "模型能力", example = "CHAT")
        @NotBlank(message = "模型能力不能为空")
        private String capability;

        @Schema(description = "调用协议", example = "openai")
        @NotBlank(message = "调用协议不能为空")
        private String protocol;

        @Schema(description = "调用入口完整地址")
        @NotBlank(message = "调用入口不能为空")
        @Size(max = 512, message = "调用入口长度不能超过512")
        private String apiBaseUrl;
    }
}

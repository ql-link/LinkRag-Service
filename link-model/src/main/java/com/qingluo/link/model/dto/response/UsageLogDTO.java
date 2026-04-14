package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用量明细 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用量明细")
public class UsageLogDTO {

    @Schema(description = "记录ID", example = "1")
    private Long id;

    @Schema(description = "配置ID", example = "1")
    private Long configId;

    @Schema(description = "厂商类型", example = "openai")
    private String providerType;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "提示词Token数", example = "100")
    private Integer promptTokens;

    @Schema(description = "补全Token数", example = "50")
    private Integer completionTokens;

    @Schema(description = "总Token数", example = "150")
    private Integer totalTokens;

    @Schema(description = "延迟(毫秒)", example = "150")
    private Integer latencyMs;

    @Schema(description = "状态", example = "success")
    private String status;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
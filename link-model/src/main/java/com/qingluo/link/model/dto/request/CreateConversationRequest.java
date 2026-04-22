package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建对话请求
 */
@Data
@Schema(description = "创建对话请求")
public class CreateConversationRequest {

    @Schema(description = "对话标题", example = "我的新对话")
    private String title;

    @NotNull(message = "数据集不能为空")
    @Schema(description = "数据集ID", example = "10001")
    private Long datasetId;

    @Schema(description = "上次使用的配置ID")
    private Long lastConfigId;
}

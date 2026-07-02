package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发布外部模型候选请求。
 */
@Data
@Schema(description = "发布外部模型候选请求")
public class PublishModelSyncCandidateRequest {

    @Schema(description = "发布时覆盖模型名；为空使用候选模型名", example = "gpt-4o")
    private String modelName;

    @Schema(description = "发布时覆盖展示名；为空使用候选展示名", example = "GPT-4o")
    private String displayName;

    @Schema(description = "发布时覆盖能力；为空使用候选推断能力", example = "CHAT")
    private String capability;

    @Schema(description = "发布时覆盖协议；为空使用候选推断协议", example = "openai")
    private String protocol;

    @Schema(description = "发布时覆盖调用入口；为空使用候选推断入口", example = "https://api.openai.com/v1/chat/completions")
    private String apiBaseUrl;
}

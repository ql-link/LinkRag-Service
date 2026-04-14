package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 保存消息请求
 */
@Data
@Schema(description = "保存消息请求")
public class SaveMessageRequest {

    @NotNull(message = "对话ID不能为空")
    @Schema(description = "对话ID", example = "1")
    private Long conversationId;

    @Schema(description = "配置ID")
    private Long configId;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @NotBlank(message = "消息角色不能为空")
    @Schema(description = "消息角色", example = "user")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容", example = "你好，请介绍一下你自己")
    private String content;

    @Schema(description = "Token数量", example = "0")
    private Integer tokenCount = 0;
}
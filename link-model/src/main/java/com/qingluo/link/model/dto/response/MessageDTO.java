package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 消息信息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息信息")
public class MessageDTO {

    @Schema(description = "消息ID", example = "1")
    private Long id;

    @Schema(description = "对话ID", example = "1")
    private Long conversationId;

    @Schema(description = "角色", example = "user")
    private String role;

    @Schema(description = "消息内容", example = "你好，请介绍一下你自己")
    private String content;

    @Schema(description = "配置ID")
    private Long configId;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "Token数量", example = "100")
    private Integer tokenCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
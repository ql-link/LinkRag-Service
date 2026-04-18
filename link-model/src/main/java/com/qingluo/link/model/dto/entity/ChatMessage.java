package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话消息表
 * 对应表：chat_message
 */
@Data
@TableName("chat_message")
@Schema(description = "对话消息")
public class ChatMessage {

    @Schema(description = "消息ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "对话ID", example = "1")
    @TableField("conversation_id")
    private Long conversationId;

    @Schema(description = "配置ID")
    @TableField("config_id")
    private Long configId;

    @Schema(description = "模型名称", example = "gpt-4")
    @TableField("model_name")
    private String modelName;

    @Schema(description = "角色", example = "user")
    private String role;

    @Schema(description = "消息内容", example = "你好，请介绍一下你自己")
    private String content;

    @Schema(description = "Token数量", example = "100")
    @TableField("token_count")
    private Integer tokenCount = 0;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;
}
package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话消息表
 * 对应表：chat_message
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("config_id")
    private Long configId;

    @TableField("model_name")
    private String modelName;

    private String role;

    private String content;

    @TableField("token_count")
    private Integer tokenCount = 0;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
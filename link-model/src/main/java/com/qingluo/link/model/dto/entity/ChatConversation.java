package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话表
 * 对应表：chat_conversation
 */
@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("last_config_id")
    private Long lastConfigId;

    @TableField("last_model_name")
    private String lastModelName;

    private String title;

    @TableField("is_pinned")
    private Boolean isPinned = false;

    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted = false;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
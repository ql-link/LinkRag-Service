package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话表
 * 对应表：chat_conversation
 */
@Data
@TableName("chat_conversation")
@Schema(description = "对话")
public class ChatConversation {

    @Schema(description = "对话ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID", example = "1")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "上次使用的配置ID")
    @TableField("dataset_id")
    private Long datasetId;

    @Schema(description = "上次使用的配置ID")
    @TableField("last_config_id")
    private Long lastConfigId;

    @Schema(description = "上次使用的模型名称", example = "gpt-4")
    @TableField("last_model_name")
    private String lastModelName;

    @Schema(description = "对话标题", example = "我的新对话")
    private String title;

    @Schema(description = "是否置顶", example = "false")
    @TableField("is_pinned")
    private Boolean isPinned = false;

    @Schema(description = "是否删除")
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted = false;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

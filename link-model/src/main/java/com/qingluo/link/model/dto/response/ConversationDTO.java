package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 对话信息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对话信息")
public class ConversationDTO {

    @Schema(description = "对话ID", example = "1")
    private Long id;

    @Schema(description = "对话标题", example = "我的新对话")
    private String title;

    @Schema(description = "上次使用的配置ID")
    private Long lastConfigId;

    @Schema(description = "上次使用的模型名称", example = "gpt-4")
    private String lastModelName;

    @Schema(description = "是否置顶", example = "false")
    private Boolean isPinned;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
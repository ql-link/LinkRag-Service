package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 更新对话请求
 */
@Data
@Schema(description = "更新对话请求")
public class UpdateConversationRequest {

    @Size(max = 255, message = "对话标题长度不能超过255")
    @Schema(description = "对话标题", example = "我的新对话标题")
    private String title;

    @Schema(description = "是否置顶", example = "true")
    private Boolean isPinned;
}


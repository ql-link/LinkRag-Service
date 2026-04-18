package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建对话请求
 */
@Data
@Schema(description = "创建对话请求")
public class CreateConversationRequest {

    @Schema(description = "对话标题", example = "我的新对话")
    private String title;

    @Schema(description = "上次使用的配置ID")
    private Long lastConfigId;
}
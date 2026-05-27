package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 发送消息请求
 */
@Data
@Schema(description = "发送消息请求")
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容", example = "你好，请介绍一下你自己")
    private String content;

    @Schema(description = "配置ID")
    private Long configId;
}


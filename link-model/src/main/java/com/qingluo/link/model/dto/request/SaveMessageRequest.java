package com.qingluo.link.model.dto.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 保存消息请求
 */
@Data
public class SaveMessageRequest {

    @NotNull(message = "对话ID不能为空")
    private Long conversationId;

    private Long configId;

    private String modelName;

    @NotBlank(message = "消息角色不能为空")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private Integer tokenCount = 0;
}
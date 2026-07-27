package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "紧急停用LLM配置")
public class EmergencyDisableLLMConfigRequest {

    @Schema(description = "USER所有者确认保留数据集绑定并接受执行失败", example = "true")
    private Boolean confirmed;

}

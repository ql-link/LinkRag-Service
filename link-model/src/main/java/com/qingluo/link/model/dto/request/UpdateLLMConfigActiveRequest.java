package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新LLM配置启用状态")
public class UpdateLLMConfigActiveRequest {

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否启用", example = "false")
    private Boolean isActive;
}

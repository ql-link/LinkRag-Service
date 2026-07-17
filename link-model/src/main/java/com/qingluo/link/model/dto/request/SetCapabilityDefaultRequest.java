package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "设置能力默认配置")
public class SetCapabilityDefaultRequest {

    @NotNull(message = "配置ID不能为空")
    @Positive(message = "配置ID必须为正整数")
    @Schema(description = "全局配置ID", example = "10001")
    private Long configId;
}

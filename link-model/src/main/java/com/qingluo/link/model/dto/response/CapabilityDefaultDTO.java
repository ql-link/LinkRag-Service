package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "能力默认选择")
public class CapabilityDefaultDTO {

    @Schema(description = "模型能力", example = "CHAT")
    private String capability;

    @Schema(description = "用户默认配置ID；未设置时为空")
    private Long configId;
}

package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员平台配置原子保存结果")
public class AdminPlatformConfigSaveResult {

    private ExecutableLLMConfigDTO config;
    private CapabilityDefaultDTO capabilityDefault;
}

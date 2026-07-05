package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 刷新外部模型目录请求。
 */
@Data
@Schema(description = "刷新外部模型目录请求")
public class SyncProviderModelsRequest {

    @Schema(description = "同步来源，当前支持 MODELS_DEV；为空默认 MODELS_DEV", example = "MODELS_DEV")
    private String syncSource;
}

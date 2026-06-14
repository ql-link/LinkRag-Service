package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型「单能力」事实明细 DTO。
 *
 * <p>暴露某 (模型, 能力) 的真实调用协议与入口，供管理端与前端看到「这个能力实际怎么调」。
 * protocol/api_base_url 来自模型能力层事实（llm_provider_model），非厂商默认。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型单能力事实明细")
public class ModelCapabilityDetailDTO {

    @Schema(description = "能力标识", example = "RERANK")
    private String capability;

    @Schema(description = "调用协议（API 家族）", example = "dashscope")
    private String protocol;

    @Schema(description = "调用入口基地址", example = "https://dashscope.aliyuncs.com/api/v1")
    private String apiBaseUrl;
}

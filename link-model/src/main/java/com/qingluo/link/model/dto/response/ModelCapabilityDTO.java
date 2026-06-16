package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型能力展示 DTO。
 *
 * <p>用于用户添加 LLM 配置前展示某个模型支持的能力集合。capabilities 由原能力名列表
 * 升级为「能力 + 协议 + 入口」明细，使管理端与前端能看到每个能力实际怎么调（事实值）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型能力信息")
public class ModelCapabilityDTO {

    @Schema(description = "模型名称", example = "gpt-4o")
    private String modelName;

    @Schema(description = "模型支持的能力明细（含协议与入口）")
    private List<ModelCapabilityDetailDTO> capabilities;
}

package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 临时展示的上游模型选项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型选项")
public class ProviderModelOptionDTO {

    @Schema(description = "模型 ID", example = "gpt-4o")
    private String id;

    @Schema(description = "展示名称", example = "gpt-4o")
    private String displayName;

    @Schema(description = "模型归属", example = "openai")
    private String ownedBy;
}

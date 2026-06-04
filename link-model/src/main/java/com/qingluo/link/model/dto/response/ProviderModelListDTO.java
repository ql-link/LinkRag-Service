package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型列表拉取响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "模型列表拉取响应")
public class ProviderModelListDTO {

    @Schema(description = "是否允许前端手动输入模型名", example = "true")
    private Boolean allowManualInput;

    @Schema(description = "模型列表")
    private List<ProviderModelOptionDTO> models;

    public static ProviderModelListDTO success(List<ProviderModelOptionDTO> models) {
        return new ProviderModelListDTO(false, models);
    }

    public static ProviderModelListDTO manual() {
        return new ProviderModelListDTO(true, List.of());
    }
}

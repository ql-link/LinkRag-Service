package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商图标上传结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "厂商图标上传结果")
public class ProviderIconUploadDTO {

    @Schema(description = "公开访问 URL", example = "https://minio.example/tolink-public/providerIcon/openai.png")
    private String iconUrl;

    @Schema(description = "OSS object key", example = "providerIcon/openai.png")
    private String iconObjectKey;
}

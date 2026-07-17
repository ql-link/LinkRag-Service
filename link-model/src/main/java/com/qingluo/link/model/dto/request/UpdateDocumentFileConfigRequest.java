package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "完整替换文档文件上传配置")
public class UpdateDocumentFileConfigRequest {

    @Schema(description = "单文件业务大小上限，单位字节", required = true)
    private Long maxSizeBytes;

    @Schema(description = "启用的后缀全集", required = true)
    private List<String> allowedSuffixes;
}

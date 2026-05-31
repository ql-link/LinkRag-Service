package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "修改文档文件上传配置请求")
public class UpdateDocumentFileConfigRequest {

    @NotNull
    @Positive
    @Schema(description = "单文件大小上限，单位字节", example = "20971520", required = true)
    private Long maxSizeBytes;

    @NotEmpty
    @Schema(description = "允许上传的文件后缀白名单", example = "[\"md\",\"pdf\",\"txt\"]", required = true)
    private List<String> allowedSuffixes;
}

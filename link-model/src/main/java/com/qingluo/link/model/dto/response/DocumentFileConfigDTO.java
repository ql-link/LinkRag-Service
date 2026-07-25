package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档文件上传配置")
public class DocumentFileConfigDTO {

    @Schema(description = "单文件大小上限，单位字节", example = "20971520")
    private Long maxSizeBytes;

    @Schema(description = "允许上传的文件后缀白名单")
    private List<String> allowedSuffixes;

    @Schema(description = "最后修改管理员 ID；使用部署默认值时为空")
    private Long updatedBy;

    @Schema(description = "最后修改时间；使用部署默认值时为空")
    private LocalDateTime updatedAt;
}

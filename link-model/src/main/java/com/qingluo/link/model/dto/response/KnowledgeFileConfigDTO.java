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
@Schema(description = "知识文件上传配置")
public class KnowledgeFileConfigDTO {

    @Schema(description = "单文件大小上限，单位字节", example = "20971520")
    private Long maxSizeBytes;

    @Schema(description = "允许上传的文件后缀白名单")
    private List<String> allowedSuffixes;

    @Schema(description = "最后修改人ID", example = "10000")
    private Long updatedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime updatedAt;
}

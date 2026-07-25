package com.qingluo.link.model.dto.response;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识文件信息")
public class DocumentFileDTO {

    private Long id;
    private Long datasetId;
    private String originalFilename;
    private String fileSuffix;
    private Long fileSize;
    private String uploadStatus;
    private Boolean isUploadSuccess;
    private String failureReason;
    @Schema(description = "Markdown 图片匹配汇总；普通文件为空")
    private MarkdownAssetSummaryDTO assetSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

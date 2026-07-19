package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识文件解析结果")
public class FileParseResultDTO {

    private Long fileId;

    private String originalFilename;

    private String parsedFilename;

    private String frontendStatus;

    private String parseStatus;

    private String failureReason;

    @Schema(description = "Markdown 图片匹配汇总；普通文件为空")
    private MarkdownAssetSummaryDTO assetSummary;
}

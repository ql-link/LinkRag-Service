package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件解析任务提交结果")
public class FileParseSubmitDTO {

    private Long fileId;

    private String originalFilename;

    private String frontendStatus;

    @Schema(description = "解析任务ID；已有运行中任务时返回同一ID")
    private String taskId;

    @Schema(description = "是否复用了已有运行中任务", example = "false")
    private Boolean alreadyRunning;

    @Schema(description = "Markdown 图片匹配汇总；普通文件为空")
    private MarkdownAssetSummaryDTO assetSummary;
}

package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件解析结果响应。
 *
 * <p>用于前端按本次文件列表汇总展示，二期不返回解析产物下载或预览地址。
 */
@Data
@Schema(description = "文件解析结果响应")
public class FileParseResultDTO {

    @Schema(description = "原文件ID", example = "10000")
    private Long fileId;

    @Schema(description = "用户上传时的原始文件名", example = "report.pdf")
    private String originalFilename;

    @Schema(description = "解析后的 Markdown 文件名预期值", example = "report.md")
    private String parsedFilename;

    /** 前端状态：uploaded/parse_waiting/parsing/parse_success/parse_failed。 */
    @Schema(description = "前端展示状态：uploaded/parse_waiting/parsing/parse_success/parse_failed", example = "uploaded")
    private String frontendStatus;

    /** 后端任务状态：created/processing/success/failed，未提交解析时为空。 */
    @Schema(description = "后端解析任务状态：created/processing/success/failed，未提交解析时为空", example = "created")
    private String parseStatus;

    /** 失败时展示业务化失败原因。 */
    @Schema(description = "失败原因，成功或未提交解析时为空", example = "文件解析失败")
    private String failureReason;
}

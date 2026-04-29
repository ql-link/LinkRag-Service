package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件解析提交响应。
 *
 * <p>二期前端不依赖 taskId 操作解析流程，因此这里只返回文件维度状态。
 */
@Data
@Schema(description = "文件解析提交响应")
public class FileParseSubmitDTO {

    /** 原文件 ID。 */
    @Schema(description = "原文件ID", example = "10000")
    private Long fileId;

    /** 用户上传时的原始文件名。 */
    @Schema(description = "用户上传时的原始文件名", example = "report.pdf")
    private String originalFilename;

    /** 前端展示状态，提交成功后固定为 parsing。 */
    @Schema(description = "前端展示状态，提交成功后固定为 parsing", example = "parsing")
    private String frontendStatus;
}

package com.qingluo.link.model.dto.response;

import lombok.Data;

/**
 * 文件解析结果响应。
 *
 * <p>用于前端按本次文件列表汇总展示，二期不返回解析产物下载或预览地址。
 */
@Data
public class FileParseResultDTO {

    private Long fileId;

    private String originalFilename;

    private String parsedFilename;

    /** 前端状态：uploaded/parse_waiting/parsing/parse_success/parse_failed。 */
    private String frontendStatus;

    /** 后端任务状态：created/processing/success/failed，未提交解析时为空。 */
    private String parseStatus;

    /** 失败时展示业务化失败原因。 */
    private String failureReason;
}

package com.qingluo.link.model.dto.response;

import lombok.Data;

/**
 * 文件解析提交响应。
 *
 * <p>二期前端不依赖 taskId 操作解析流程，因此这里只返回文件维度状态。
 */
@Data
public class FileParseSubmitDTO {

    /** 原文件 ID。 */
    private Long fileId;

    /** 用户上传时的原始文件名。 */
    private String originalFilename;

    /** 前端展示状态，提交成功后固定为 parse_waiting。 */
    private String frontendStatus;
}

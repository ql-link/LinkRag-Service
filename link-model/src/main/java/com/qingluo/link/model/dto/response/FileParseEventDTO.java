package com.qingluo.link.model.dto.response;

import lombok.Data;

/**
 * SSE 推送给前端的解析事件。
 */
@Data
public class FileParseEventDTO {

    private Long fileId;

    private String originalFilename;

    private String frontendStatus;

    private Integer progress;

    private String parseStatus;

    private String failureReason;
}

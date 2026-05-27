package com.qingluo.link.model.dto.response;

import lombok.Data;

@Data
public class FileParseEventDTO {

    private Long fileId;

    private String originalFilename;

    private String frontendStatus;

    private Integer progress;

    private String parseStatus;

    private String failureReason;
}

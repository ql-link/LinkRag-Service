package com.qingluo.link.model.dto.response;

import lombok.Data;

@Data
public class FileParseResultDTO {

    private Long fileId;

    private String originalFilename;

    private String parsedFilename;

    private String frontendStatus;

    private String parseStatus;

    private String failureReason;
}

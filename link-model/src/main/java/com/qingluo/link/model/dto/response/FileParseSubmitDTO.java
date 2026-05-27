package com.qingluo.link.model.dto.response;

import lombok.Data;

@Data
public class FileParseSubmitDTO {

    private Long fileId;

    private String originalFilename;

    private String frontendStatus;
}

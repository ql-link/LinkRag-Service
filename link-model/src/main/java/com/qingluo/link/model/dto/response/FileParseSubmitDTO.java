package com.qingluo.link.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class FileParseSubmitDTO {

    private Long fileId;

    private String originalFilename;

    private String frontendStatus;

    private List<String> missingAssets;

    private Boolean canContinue;
}

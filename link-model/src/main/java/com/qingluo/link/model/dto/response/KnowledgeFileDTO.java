package com.qingluo.link.model.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class KnowledgeFileDTO {

    private Long id;
    private Long datasetId;
    private String originalFilename;
    private String fileSuffix;
    private Long fileSize;
    private String uploadStatus;
    private Boolean isUploadSuccess;
    private String parseNoticeStatus;
    private String parseTaskId;
    private String parseStatus;
    private Boolean isParseSuccess;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

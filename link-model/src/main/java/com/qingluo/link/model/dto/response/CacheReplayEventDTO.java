package com.qingluo.link.model.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CacheReplayEventDTO {

    private Long id;
    private String eventKey;
    private String stage;
    private String sourceTopic;
    private Integer partitionNo;
    private Long sourceOffset;
    private String failureReason;
    private String errorMessage;
    private String status;
    private Integer failCount;
    private LocalDateTime firstFailedAt;
    private LocalDateTime lastFailedAt;
    private LocalDateTime replayedAt;
}

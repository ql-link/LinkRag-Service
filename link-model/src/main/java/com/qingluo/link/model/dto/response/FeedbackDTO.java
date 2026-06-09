package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "Feedback response")
public class FeedbackDTO {

    private Long id;
    private String type;
    private String title;
    private String content;
    private String attachmentObjectKey;
    private String status;
    private Integer priority;
    private Long adminId;
    private String adminReply;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

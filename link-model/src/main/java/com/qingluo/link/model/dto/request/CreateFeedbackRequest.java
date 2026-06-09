package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create anonymous feedback request")
public class CreateFeedbackRequest {

    @Schema(description = "Feedback type: BUG, FEATURE, EXPERIENCE, OTHER", example = "BUG")
    private String type;

    @NotBlank(message = "feedback title is required")
    @Size(max = 128, message = "feedback title must be at most 128 characters")
    @Schema(description = "Feedback title", required = true)
    private String title;

    @NotBlank(message = "feedback content is required")
    @Size(max = 5000, message = "feedback content must be at most 5000 characters")
    @Schema(description = "Feedback content", required = true)
    private String content;
}

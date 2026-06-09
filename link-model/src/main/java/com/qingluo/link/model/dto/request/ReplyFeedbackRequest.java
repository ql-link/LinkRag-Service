package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Reply feedback request")
public class ReplyFeedbackRequest {

    @NotBlank(message = "feedback reply is required")
    @Size(max = 5000, message = "feedback reply must be at most 5000 characters")
    @Schema(description = "Admin reply", required = true)
    private String reply;
}

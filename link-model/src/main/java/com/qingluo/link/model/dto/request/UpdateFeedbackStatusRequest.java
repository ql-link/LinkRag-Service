package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Update feedback status request")
public class UpdateFeedbackStatusRequest {

    @NotBlank(message = "feedback status is required")
    @Schema(description = "PENDING, PROCESSING, RESOLVED, CLOSED", required = true)
    private String status;
}

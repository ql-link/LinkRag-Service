package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update feedback priority request")
public class UpdateFeedbackPriorityRequest {

    @NotNull(message = "feedback priority is required")
    @Min(value = 1, message = "feedback priority must be between 1 and 3")
    @Max(value = 3, message = "feedback priority must be between 1 and 3")
    @Schema(description = "1=high, 2=medium, 3=low", required = true)
    private Integer priority;
}

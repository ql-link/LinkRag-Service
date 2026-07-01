package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 更新外部模型候选审核状态请求。
 */
@Data
@Schema(description = "更新外部模型候选审核状态请求")
public class UpdateModelSyncCandidateReviewRequest {

    @NotBlank(message = "审核状态不能为空")
    @Schema(description = "审核状态：PENDING/REJECTED", example = "REJECTED")
    private String reviewStatus;
}

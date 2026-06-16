package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "反馈响应")
public class FeedbackDTO {

    @Schema(description = "反馈 ID")
    private Long id;
    @Schema(description = "反馈类型")
    private String type;
    @Schema(description = "反馈标题")
    private String title;
    @Schema(description = "反馈详细内容")
    private String content;
    @Schema(description = "附件公开桶对象 key")
    private String attachmentObjectKey;
    @Schema(description = "附件可访问公开 URL（由 objectKey 拼装，无附件为空）")
    private String attachmentUrl;
    @Schema(description = "处理状态")
    private String status;
    @Schema(description = "处理优先级")
    private Integer priority;
    @Schema(description = "处理该反馈的管理员用户 ID")
    private Long adminId;
    @Schema(description = "管理员回复或处理结论")
    private String adminReply;
    @Schema(description = "管理员最后处理时间")
    private LocalDateTime processedAt;
    @Schema(description = "反馈提交时间")
    private LocalDateTime createdAt;
    @Schema(description = "反馈更新时间")
    private LocalDateTime updatedAt;
}

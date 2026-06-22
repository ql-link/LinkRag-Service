package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息信息 DTO（一行一轮：query + answer 同行）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息信息（一行一轮）")
public class MessageDTO {

    @Schema(description = "消息ID", example = "1")
    private Long id;

    @Schema(description = "对话ID", example = "1")
    private Long conversationId;

    @Schema(description = "用户提问", example = "什么是RAG")
    private String query;

    @Schema(description = "LLM回答")
    private String answer;

    @Schema(description = "配置ID")
    private Long configId;

    @Schema(description = "模型名快照", example = "gpt-4")
    private String modelName;

    @Schema(description = "召回片段 chunk_id 列表")
    private List<String> references;

    @Schema(description = "请求追踪ID/幂等键", example = "req-20260619-001")
    private String requestId;

    @Schema(description = "轮次状态：success/partial/failed", example = "success")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

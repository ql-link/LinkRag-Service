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

    @Schema(description = "轮次幂等键（前端每轮稳定 UUID）", example = "turn-20260623-001")
    private String turnId;

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

    @Schema(description = "轮次状态：GENERATING/COMPLETED/FAILED", example = "COMPLETED")
    private String status;

    @Schema(description = "失败错误码（仅 FAILED）：RECALL_* 或 GENERATION_TIMEOUT")
    private String errorCode;

    @Schema(description = "失败错误信息（仅 FAILED，不含堆栈）")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话消息表（一行一轮：query + answer 同行）。
 *
 * <p>行数据所有权在 Java，由消费 {@code tolink.rag.chat_turn} 消息后落库；
 * Python 仅作为无状态问答执行器，不直接写本表。</p>
 *
 * <p>{@code autoResultMap = true} 必须开，否则查询回填 references 不走
 * {@link JacksonTypeHandler}（MyBatis-Plus 默认 TypeHandler 仅作用于写）。</p>
 *
 * 对应表：chat_message
 */
@Data
@TableName(value = "chat_message", autoResultMap = true)
@Schema(description = "对话消息（一行一轮）")
public class ChatMessage {

    @Schema(description = "消息ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "对话ID", example = "1")
    @TableField("conversation_id")
    private Long conversationId;

    @Schema(description = "本轮所用 LLM 配置ID")
    @TableField("config_id")
    private Long configId;

    @Schema(description = "模型名快照", example = "gpt-4")
    @TableField("model_name")
    private String modelName;

    @Schema(description = "用户提问", example = "什么是RAG")
    private String query;

    @Schema(description = "LLM回答（partial 为半截，failed 可空）")
    private String answer;

    @Schema(description = "召回片段 chunk_id 列表（仅标识，不含正文）")
    @TableField(value = "`references`", typeHandler = JacksonTypeHandler.class)
    private List<String> references;

    @Schema(description = "请求追踪ID/幂等键", example = "req-20260619-001")
    @TableField("request_id")
    private String requestId;

    @Schema(description = "轮次状态：success/partial/failed", example = "success")
    private String status;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;
}

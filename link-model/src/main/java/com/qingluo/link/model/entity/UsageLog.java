package com.qingluo.link.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 调用用量日志表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("llm_usage_log")
public class UsageLog {

    /**
     * 记录唯一标识
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 用户配置 ID
     */
    private String configId;

    /**
     * 厂商类型
     */
    private String providerType;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 输入 Token 数
     */
    private Integer promptTokens;

    /**
     * 输出 Token 数
     */
    private Integer completionTokens;

    /**
     * 总 Token 数
     */
    private Integer totalTokens;

    /**
     * 响应延迟(毫秒)
     */
    private Integer latencyMs;

    /**
     * 调用状态：success/failed/partial
     */
    private String status;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 触发 Fallback 时记录原配置 ID
     */
    private String fallbackConfigId;

    /**
     * 关联对话 ID
     */
    private String conversationId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
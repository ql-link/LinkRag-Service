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
 * 用户级 LLM 配置表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("llm_user_config")
public class UserLLMConfig {

    /**
     * 配置唯一标识 (UUID)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 关联 SystemProvider ID
     */
    private String providerId;

    /**
     * 厂商类型快照，如 openai
     */
    private String providerType;

    /**
     * 厂商名称快照，如 "OpenAI"
     */
    private String providerName;

    /**
     * 用户自定义配置名称
     */
    private String configName;

    /**
     * 用户提供的 API Key（加密存储）
     */
    private String apiKey;

    /**
     * 自定义 API 地址（覆盖系统默认）
     */
    private String customApiBaseUrl;

    /**
     * 具体模型名，如 "gpt-4"
     */
    private String modelName;

    /**
     * 优先级 1-100
     */
    private Integer priority;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 是否为用户默认配置
     */
    private Boolean isDefault;

    /**
     * 超时时间(毫秒)
     */
    private Integer timeoutMs;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 是否支持流式输出
     */
    private Boolean streamEnabled;

    /**
     * 模型能力快照
     */
    private String capabilities;

    /**
     * 扩展配置（覆盖系统默认参数）
     */
    private String extraConfig;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
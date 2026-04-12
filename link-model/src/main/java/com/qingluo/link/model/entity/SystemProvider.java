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
 * LLM 系统级厂商配置表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("llm_system_provider")
public class SystemProvider {

    /**
     * 厂商唯一标识 (UUID)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 厂商类型：openai/claude/glm/deepseek
     */
    private String providerType;

    /**
     * 厂商展示名称，如 "OpenAI"
     */
    private String providerName;

    /**
     * 官方默认 API 地址
     */
    private String apiBaseUrl;

    /**
     * 支持模型与能力映射，如 {"gpt-4":["CHAT","OCR"]}
     */
    private String supportedModels;

    /**
     * 配置参数 Schema（用于前端表单渲染）
     */
    private String configSchema;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 厂商优先级（1-100）
     */
    private Integer priority;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户级 LLM 配置表
 * 对应表：llm_user_config
 *
 * <p>本表只保存用户自配配置。Java 先按 (user_id, capability, is_default, is_active)
 * 查用户自配默认；没有时再回退到 llm_system_preset 的 LinkRag 系统默认。
 * 一个 (用户,厂商,模型,能力) 一行，同厂商多行共用同一个厂商级 api_key。</p>
 */
@Data
@TableName("llm_user_config")
@Schema(description = "用户LLM配置")
public class UserLLMConfig {

    @Schema(description = "配置ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID", example = "1")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "厂商ID", example = "1")
    @TableField("provider_id")
    private Long providerId;

    @Schema(description = "厂商类型", example = "openai")
    @TableField("provider_type")
    private String providerType;

    @Schema(description = "API Key（厂商级，加密存储）")
    @TableField("api_key")
    private String apiKey;

    /** 运行快照：展开时复制自模型能力层事实值（绝不 fallback 厂商默认），下游直读总能拿到可用地址。 */
    @Schema(description = "API地址")
    @TableField("api_base_url")
    private String apiBaseUrl;

    /** 运行快照：复制自模型能力层 protocol，下游按 protocol+capability 选 adapter，不再查厂商或模型表。 */
    @Schema(description = "调用协议", example = "openai")
    @TableField("protocol")
    private String protocol;

    @Schema(description = "模型名称", example = "gpt-4")
    @TableField("model_name")
    private String modelName;

    @Schema(description = "专用能力标识：CHAT/EMBEDDING/SPARSE_EMBEDDING/RERANK", example = "CHAT")
    @TableField("capability")
    private String capability = "CHAT";

    /** 模型启停 + 生效过滤双重语义：关停某模型即把它全部能力行置 false，既退出候选、又取不到为生效。 */
    @Schema(description = "是否启用", example = "true")
    @TableField("is_active")
    private Boolean isActive = true;

    @Schema(description = "该能力是否生效（单用户单能力唯一）", example = "false")
    @TableField("is_default")
    private Boolean isDefault = false;

    /** 历史兼容字段：新注册用户不再写系统预设镜像行，正常用户自配行恒为 false。 */
    @Schema(description = "历史兼容字段；新用户自配配置恒为 false", example = "false")
    @TableField("is_system_preset")
    private Boolean isSystemPreset = false;

    @Schema(description = "创建时间")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

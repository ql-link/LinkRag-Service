package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 某个主体在一种能力下的默认配置指针。
 */
@Data
@TableName("llm_capability_default")
@Schema(description = "LLM能力默认关系")
public class LLMCapabilityDefault {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("scope")
    @Schema(description = "默认范围：SYSTEM/USER", example = "USER")
    private String scope;

    @TableField("owner_user_id")
    @Schema(description = "所有者用户ID；SYSTEM固定为0", example = "7")
    private Long ownerUserId;

    @TableField("capability")
    @Schema(description = "模型能力", example = "CHAT")
    private String capability;

    @TableField("config_id")
    @Schema(description = "默认配置的全局ID", example = "10001")
    private Long configId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

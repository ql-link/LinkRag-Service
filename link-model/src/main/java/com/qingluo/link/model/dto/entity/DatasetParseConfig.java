package com.qingluo.link.model.dto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 跨端共享表 {@code dataset_parse_config} 实体（Java 读写、Python 直读同一行）。
 *
 * <p>四个 JSON 列用 {@link JacksonTypeHandler} 做「JSON 文本 ↔ 值对象」转换；
 * {@code autoResultMap = true} 必须开，否则查询回填不走 TypeHandler（MP 默认 TypeHandler 仅作用于写）。
 */
@Data
@TableName(value = "dataset_parse_config", autoResultMap = true)
public class DatasetParseConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField(value = "chunking_config", typeHandler = JacksonTypeHandler.class)
    private ChunkingConfig chunkingConfig;

    @TableField(value = "enhancement_config", typeHandler = JacksonTypeHandler.class)
    private EnhancementConfig enhancementConfig;

    @TableField(value = "pdf_config", typeHandler = JacksonTypeHandler.class)
    private PdfConfig pdfConfig;

    @TableField(value = "recall_config", typeHandler = JacksonTypeHandler.class)
    private RecallConfig recallConfig;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

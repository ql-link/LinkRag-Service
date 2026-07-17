package com.qingluo.link.model.dto.cache;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.EnhancementConfig;
import com.qingluo.link.model.dto.config.PdfConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code dataset_parse_config} 的跨语言缓存投影。
 *
 * <p>只保存数据库原始事实。Java 的展示默认值和 Python 的运行期 Settings
 * 都必须在命中缓存后于各自进程内合并，不能写回本对象。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DatasetParseConfigSnapshot {

    private Long userId;
    private Long datasetId;
    private Long sparseEmbeddingConfigId;
    private Long denseEmbeddingConfigId;
    private Long enhancementChatConfigId;
    private Long enhancementVisionConfigId;
    private Long rerankConfigId;
    private ChunkingConfig chunkingConfig;
    private EnhancementConfig enhancementConfig;
    private PdfConfig pdfConfig;
    private RecallConfig recallConfig;
    private Boolean isActive;
}

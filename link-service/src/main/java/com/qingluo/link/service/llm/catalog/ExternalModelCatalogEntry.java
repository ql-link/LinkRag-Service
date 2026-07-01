package com.qingluo.link.service.llm.catalog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 外部模型目录归一化条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalModelCatalogEntry {

    private String externalModelId;
    private String modelName;
    private String displayName;
    private List<String> capabilities;
    private String inputModalitiesJson;
    private String outputModalitiesJson;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private LocalDate releaseDate;
    private String rawMetadataJson;
}

package com.qingluo.link.service.llm.catalog;

import com.qingluo.link.model.dto.entity.SystemProvider;

import java.util.List;

/**
 * 外部模型目录来源适配器。
 */
public interface ExternalModelCatalogClient {

    /**
     * 来源标识。
     */
    String source();

    /**
     * 拉取某厂商的外部模型目录。
     */
    List<ExternalModelCatalogEntry> listModels(SystemProvider provider);
}

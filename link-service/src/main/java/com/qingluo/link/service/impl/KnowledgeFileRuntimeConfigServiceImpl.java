package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.mapper.KnowledgeFileConfigMapper;
import com.qingluo.link.model.dto.entity.KnowledgeFileConfig;
import com.qingluo.link.service.KnowledgeFileRuntimeConfigService;
import com.qingluo.link.service.config.KnowledgeFileConfigNormalizer;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.config.KnowledgeFileRuntimeConfig;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KnowledgeFileRuntimeConfigServiceImpl implements KnowledgeFileRuntimeConfigService {

    private final KnowledgeFileConfigMapper knowledgeFileConfigMapper;
    private final KnowledgeFileProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public KnowledgeFileRuntimeConfig getCurrent() {
        KnowledgeFileConfig config = knowledgeFileConfigMapper.selectOne(new LambdaQueryWrapper<KnowledgeFileConfig>()
            .orderByDesc(KnowledgeFileConfig::getId)
            .last("LIMIT 1"));
        if (config == null) {
            return new KnowledgeFileRuntimeConfig(properties.getMaxSizeBytes(),
                new LinkedHashSet<>(properties.getAllowedSuffixes()));
        }

        return new KnowledgeFileRuntimeConfig(
            config.getMaxSizeBytes() == null ? properties.getMaxSizeBytes() : config.getMaxSizeBytes(),
            KnowledgeFileConfigNormalizer.parseSuffixes(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes(), objectMapper));
    }
}

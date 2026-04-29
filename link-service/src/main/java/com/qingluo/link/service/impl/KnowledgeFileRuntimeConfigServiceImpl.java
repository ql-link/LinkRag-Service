package com.qingluo.link.service.impl;

import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.service.KnowledgeFileRuntimeConfigService;
import com.qingluo.link.service.cache.KnowledgeFileConfigCacheService;
import com.qingluo.link.service.config.KnowledgeFileConfigNormalizer;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.config.KnowledgeFileRuntimeConfig;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeFileRuntimeConfigServiceImpl implements KnowledgeFileRuntimeConfigService {

    private final KnowledgeFileProperties properties;
    private final KnowledgeFileConfigCacheService knowledgeFileConfigCacheService;

    @Override
    public KnowledgeFileRuntimeConfig getCurrent() {
        KnowledgeFileConfigDTO config = knowledgeFileConfigCacheService.getConfig().orElse(null);
        if (config == null || config.getMaxSizeBytes() == null || config.getMaxSizeBytes() <= 0) {
            return defaultConfig();
        }
        Set<String> suffixes = KnowledgeFileConfigNormalizer.normalizeOrFallback(
            config.getAllowedSuffixes(), properties.getAllowedSuffixes());
        if (suffixes.isEmpty()) {
            log.warn("Ignore invalid knowledge file upload config from Redis because suffixes are empty");
            return defaultConfig();
        }
        return new KnowledgeFileRuntimeConfig(config.getMaxSizeBytes(), suffixes);
    }

    private KnowledgeFileRuntimeConfig defaultConfig() {
        return new KnowledgeFileRuntimeConfig(properties.getMaxSizeBytes(),
            new LinkedHashSet<>(properties.getAllowedSuffixes()));
    }
}

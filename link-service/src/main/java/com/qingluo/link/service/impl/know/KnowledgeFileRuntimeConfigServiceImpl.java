package com.qingluo.link.service.impl.know;

import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.service.KnowledgeFileRuntimeConfigService;
import com.qingluo.link.service.cache.KnowledgeFileConfigCacheService;
import com.qingluo.link.service.config.KnowledgeFileConfigNormalizer;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import com.qingluo.link.service.config.KnowledgeFileRuntimeConfig;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeFileRuntimeConfigServiceImpl implements KnowledgeFileRuntimeConfigService {

    private final KnowledgeFileProperties properties;
    private final KnowledgeFileConfigCacheService cacheService;

    @Override
    public KnowledgeFileRuntimeConfig getCurrent() {
        try {
            return cacheService.getConfig()
                .filter(this::isUsableConfig)
                .map(config -> new KnowledgeFileRuntimeConfig(
                    config.getMaxSizeBytes(),
                    KnowledgeFileConfigNormalizer.normalizeOrFallback(
                        config.getAllowedSuffixes(), properties.getAllowedSuffixes())))
                .orElseGet(this::defaultConfig);
        } catch (RuntimeException ex) {
            log.warn("Read knowledge file runtime config failed; fallback to default config, error={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage());
            return defaultConfig();
        }
    }

    private boolean isUsableConfig(KnowledgeFileConfigDTO config) {
        return config.getMaxSizeBytes() != null
            && config.getMaxSizeBytes() > 0
            && KnowledgeFileConfigNormalizer.hasOnlySupportedSuffixes(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes());
    }

    private KnowledgeFileRuntimeConfig defaultConfig() {
        return new KnowledgeFileRuntimeConfig(properties.getMaxSizeBytes(),
            new LinkedHashSet<>(properties.getAllowedSuffixes()));
    }
}

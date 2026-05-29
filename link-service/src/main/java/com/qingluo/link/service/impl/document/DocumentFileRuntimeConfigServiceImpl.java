package com.qingluo.link.service.impl.document;

import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.service.DocumentFileRuntimeConfigService;
import com.qingluo.link.service.cache.DocumentFileConfigCacheService;
import com.qingluo.link.service.config.DocumentFileConfigNormalizer;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentFileRuntimeConfigServiceImpl implements DocumentFileRuntimeConfigService {

    private final DocumentFileProperties properties;
    private final DocumentFileConfigCacheService cacheService;

    @Override
    public DocumentFileRuntimeConfig getCurrent() {
        try {
            return cacheService.getConfig()
                .filter(this::isUsableConfig)
                .map(config -> new DocumentFileRuntimeConfig(
                    config.getMaxSizeBytes(),
                    DocumentFileConfigNormalizer.normalizeOrFallback(
                        config.getAllowedSuffixes(), properties.getAllowedSuffixes())))
                .orElseGet(this::defaultConfig);
        } catch (RuntimeException ex) {
            log.warn("Read document file runtime config failed; fallback to default config, error={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage());
            return defaultConfig();
        }
    }

    private boolean isUsableConfig(DocumentFileConfigDTO config) {
        return config.getMaxSizeBytes() != null
            && config.getMaxSizeBytes() > 0
            && DocumentFileConfigNormalizer.hasOnlySupportedSuffixes(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes());
    }

    private DocumentFileRuntimeConfig defaultConfig() {
        return new DocumentFileRuntimeConfig(properties.getMaxSizeBytes(),
            new LinkedHashSet<>(properties.getAllowedSuffixes()));
    }
}

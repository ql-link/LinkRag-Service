package com.qingluo.link.service.config;

import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.service.cache.DocumentFileConfigCacheService;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentFileConfigInitializer {

    private final DocumentFileProperties properties;
    private final DocumentFileConfigCacheService cacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        DocumentFileConfigDTO defaultConfig = new DocumentFileConfigDTO(
            properties.getMaxSizeBytes(),
            List.copyOf(new LinkedHashSet<>(properties.getAllowedSuffixes())),
            null,
            null
        );
        try {
            boolean initialized = cacheService.putConfigIfAbsent(defaultConfig);
            if (initialized) {
                log.info("Initialized document file upload config in Redis from application defaults");
            }
        } catch (RuntimeException ex) {
            log.warn("Initialize document file upload config in Redis failed; default config remains available, error={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}

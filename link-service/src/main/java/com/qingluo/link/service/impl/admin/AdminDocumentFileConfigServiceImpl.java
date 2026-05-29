package com.qingluo.link.service.impl.admin;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.request.UpdateDocumentFileConfigRequest;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.AdminDocumentFileConfigService;
import com.qingluo.link.service.cache.DocumentFileConfigCacheService;
import com.qingluo.link.service.config.DocumentFileConfigNormalizer;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDocumentFileConfigServiceImpl implements AdminDocumentFileConfigService {

    private final DocumentFileProperties properties;
    private final DocumentFileConfigCacheService cacheService;

    @Override
    public DocumentFileConfigDTO getCurrentConfig() {
        try {
            return cacheService.getConfig()
                .filter(this::isUsableConfig)
                .map(this::normalizeDto)
                .orElseGet(this::defaultConfig);
        } catch (RuntimeException ex) {
            log.warn("Read document file upload config failed; return default config, error={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage());
            return defaultConfig();
        }
    }

    @Override
    public void updateConfig(Long adminUserId, UpdateDocumentFileConfigRequest request) {
        if (request == null || request.getMaxSizeBytes() == null || request.getMaxSizeBytes() <= 0) {
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_CONFIG_INVALID);
        }

        LinkedHashSet<String> normalizedSuffixes =
            DocumentFileConfigNormalizer.normalizeAndValidate(request.getAllowedSuffixes(), properties.getAllowedSuffixes());
        try {
            cacheService.putConfig(new DocumentFileConfigDTO(
                request.getMaxSizeBytes(),
                List.copyOf(normalizedSuffixes),
                adminUserId,
                LocalDateTime.now()
            ));
        } catch (RuntimeException ex) {
            throw new BusinessException(500, "配置保存失败，请稍后重试", 500);
        }
    }

    private DocumentFileConfigDTO defaultConfig() {
        return new DocumentFileConfigDTO(
            properties.getMaxSizeBytes(),
            List.copyOf(new LinkedHashSet<>(properties.getAllowedSuffixes())),
            null,
            null
        );
    }

    private boolean isUsableConfig(DocumentFileConfigDTO config) {
        return config.getMaxSizeBytes() != null
            && config.getMaxSizeBytes() > 0
            && DocumentFileConfigNormalizer.hasOnlySupportedSuffixes(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes());
    }

    private DocumentFileConfigDTO normalizeDto(DocumentFileConfigDTO config) {
        return new DocumentFileConfigDTO(
            config.getMaxSizeBytes(),
            List.copyOf(DocumentFileConfigNormalizer.normalizeOrFallback(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes())),
            config.getUpdatedBy(),
            config.getUpdatedAt()
        );
    }
}

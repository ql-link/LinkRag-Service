package com.qingluo.link.service.impl.admin;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.request.UpdateKnowledgeFileConfigRequest;
import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.AdminKnowledgeFileConfigService;
import com.qingluo.link.service.cache.KnowledgeFileConfigCacheService;
import com.qingluo.link.service.config.KnowledgeFileConfigNormalizer;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminKnowledgeFileConfigServiceImpl implements AdminKnowledgeFileConfigService {

    private final KnowledgeFileProperties properties;
    private final KnowledgeFileConfigCacheService cacheService;

    @Override
    public KnowledgeFileConfigDTO getCurrentConfig() {
        try {
            return cacheService.getConfig()
                .filter(this::isUsableConfig)
                .map(this::normalizeDto)
                .orElseGet(this::defaultConfig);
        } catch (RuntimeException ex) {
            log.warn("Read knowledge file upload config failed; return default config, error={}: {}",
                ex.getClass().getSimpleName(), ex.getMessage());
            return defaultConfig();
        }
    }

    @Override
    public void updateConfig(Long adminUserId, UpdateKnowledgeFileConfigRequest request) {
        if (request == null || request.getMaxSizeBytes() == null || request.getMaxSizeBytes() <= 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_CONFIG_INVALID);
        }

        LinkedHashSet<String> normalizedSuffixes =
            KnowledgeFileConfigNormalizer.normalizeAndValidate(request.getAllowedSuffixes(), properties.getAllowedSuffixes());
        try {
            cacheService.putConfig(new KnowledgeFileConfigDTO(
                request.getMaxSizeBytes(),
                List.copyOf(normalizedSuffixes),
                adminUserId,
                LocalDateTime.now()
            ));
        } catch (RuntimeException ex) {
            throw new BusinessException(500, "配置保存失败，请稍后重试", 500);
        }
    }

    private KnowledgeFileConfigDTO defaultConfig() {
        return new KnowledgeFileConfigDTO(
            properties.getMaxSizeBytes(),
            List.copyOf(new LinkedHashSet<>(properties.getAllowedSuffixes())),
            null,
            null
        );
    }

    private boolean isUsableConfig(KnowledgeFileConfigDTO config) {
        return config.getMaxSizeBytes() != null
            && config.getMaxSizeBytes() > 0
            && KnowledgeFileConfigNormalizer.hasOnlySupportedSuffixes(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes());
    }

    private KnowledgeFileConfigDTO normalizeDto(KnowledgeFileConfigDTO config) {
        return new KnowledgeFileConfigDTO(
            config.getMaxSizeBytes(),
            List.copyOf(KnowledgeFileConfigNormalizer.normalizeOrFallback(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes())),
            config.getUpdatedBy(),
            config.getUpdatedAt()
        );
    }
}

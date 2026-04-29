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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminKnowledgeFileConfigServiceImpl implements AdminKnowledgeFileConfigService {

    private static final Set<String> SUPPORTED_SUFFIXES =
        Set.of("md", "markdown", "pdf", "docx", "txt");

    private final KnowledgeFileProperties properties;
    private final KnowledgeFileConfigCacheService knowledgeFileConfigCacheService;

    @Override
    public KnowledgeFileConfigDTO getCurrentConfig() {
        return knowledgeFileConfigCacheService.getConfig()
            .orElseGet(() -> new KnowledgeFileConfigDTO(
                properties.getMaxSizeBytes(),
                List.copyOf(new LinkedHashSet<>(properties.getAllowedSuffixes())),
                null,
                null));
    }

    @Override
    public void updateConfig(Long adminUserId, UpdateKnowledgeFileConfigRequest request) {
        if (request == null || request.getMaxSizeBytes() == null || request.getMaxSizeBytes() <= 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_CONFIG_INVALID);
        }

        LinkedHashSet<String> normalizedSuffixes =
            KnowledgeFileConfigNormalizer.normalizeAndValidate(request.getAllowedSuffixes(), SUPPORTED_SUFFIXES);
        try {
            knowledgeFileConfigCacheService.putConfig(new KnowledgeFileConfigDTO(
                request.getMaxSizeBytes(),
                List.copyOf(normalizedSuffixes),
                adminUserId,
                LocalDateTime.now()));
        } catch (RuntimeException e) {
            throw new BusinessException(500, "配置保存失败，请稍后重试", 500);
        }
    }
}

package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.KnowledgeFileConfigMapper;
import com.qingluo.link.model.dto.entity.KnowledgeFileConfig;
import com.qingluo.link.model.dto.request.UpdateKnowledgeFileConfigRequest;
import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.AdminKnowledgeFileConfigService;
import com.qingluo.link.service.config.KnowledgeFileConfigNormalizer;
import com.qingluo.link.service.config.KnowledgeFileProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminKnowledgeFileConfigServiceImpl implements AdminKnowledgeFileConfigService {

    private static final Set<String> SUPPORTED_SUFFIXES =
        Set.of("md", "markdown", "pdf", "docx", "txt");

    private final KnowledgeFileConfigMapper knowledgeFileConfigMapper;
    private final KnowledgeFileProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public KnowledgeFileConfigDTO getCurrentConfig() {
        KnowledgeFileConfig config = knowledgeFileConfigMapper.selectOne(new LambdaQueryWrapper<KnowledgeFileConfig>()
            .orderByDesc(KnowledgeFileConfig::getId)
            .last("LIMIT 1"));
        if (config == null) {
            return new KnowledgeFileConfigDTO(
                properties.getMaxSizeBytes(),
                List.copyOf(new LinkedHashSet<>(properties.getAllowedSuffixes())),
                null,
                null
            );
        }

        return new KnowledgeFileConfigDTO(
            config.getMaxSizeBytes(),
            List.copyOf(KnowledgeFileConfigNormalizer.parseSuffixes(
                config.getAllowedSuffixes(), properties.getAllowedSuffixes(), objectMapper)),
            config.getUpdatedBy(),
            config.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public void updateConfig(Long adminUserId, UpdateKnowledgeFileConfigRequest request) {
        if (request == null || request.getMaxSizeBytes() == null || request.getMaxSizeBytes() <= 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_FILE_CONFIG_INVALID);
        }

        LinkedHashSet<String> normalizedSuffixes =
            KnowledgeFileConfigNormalizer.normalizeAndValidate(request.getAllowedSuffixes(), SUPPORTED_SUFFIXES);
        KnowledgeFileConfig config = new KnowledgeFileConfig();
        config.setMaxSizeBytes(request.getMaxSizeBytes());
        config.setAllowedSuffixes(KnowledgeFileConfigNormalizer.writeSuffixes(normalizedSuffixes, objectMapper));
        config.setUpdatedBy(adminUserId);
        knowledgeFileConfigMapper.insert(config);
    }
}

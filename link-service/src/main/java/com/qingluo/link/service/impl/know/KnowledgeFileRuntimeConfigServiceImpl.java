package com.qingluo.link.service.impl.know;

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

/**
 * 知识文件运行时配置服务实现，负责合并默认配置与数据库覆盖配置。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeFileRuntimeConfigServiceImpl implements KnowledgeFileRuntimeConfigService {

    private final KnowledgeFileProperties properties;
    private final KnowledgeFileConfigCacheService knowledgeFileConfigCacheService;

    @Override
    /**
     * 获取当前生效的知识文件运行时配置，数据库为空时使用默认配置。
     */
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

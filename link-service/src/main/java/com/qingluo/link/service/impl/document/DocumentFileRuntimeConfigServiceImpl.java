package com.qingluo.link.service.impl.document;

import com.qingluo.link.service.DocumentFileRuntimeConfigService;
import com.qingluo.link.service.config.DocumentFileConfigNormalizer;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentFileRuntimeConfigServiceImpl implements DocumentFileRuntimeConfigService {

    private final DocumentFileProperties properties;

    @Override
    public DocumentFileRuntimeConfig getCurrent() {
        return defaultConfig();
    }

    private DocumentFileRuntimeConfig defaultConfig() {
        return new DocumentFileRuntimeConfig(
            properties.getMaxSizeBytes(),
            DocumentFileConfigNormalizer.normalizeOrFallback(
                List.copyOf(properties.getAllowedSuffixes()), properties.getAllowedSuffixes()));
    }
}

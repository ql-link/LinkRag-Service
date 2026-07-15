package com.qingluo.link.service.impl.admin;

import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.service.AdminDocumentFileConfigService;
import com.qingluo.link.service.config.DocumentFileConfigNormalizer;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDocumentFileConfigServiceImpl implements AdminDocumentFileConfigService {

    private final DocumentFileProperties properties;

    @Override
    public DocumentFileConfigDTO getCurrentConfig() {
        return defaultConfig();
    }

    private DocumentFileConfigDTO defaultConfig() {
        return new DocumentFileConfigDTO(
            properties.getMaxSizeBytes(),
            List.copyOf(DocumentFileConfigNormalizer.normalizeOrFallback(
                List.copyOf(properties.getAllowedSuffixes()), properties.getAllowedSuffixes())),
            null,
            null
        );
    }

}

package com.qingluo.link.service.impl.document;

import com.qingluo.link.service.DocumentFileRuntimeConfigService;
import com.qingluo.link.service.config.DocumentFileConfigSnapshot;
import com.qingluo.link.service.config.DocumentFileConfigStore;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentFileRuntimeConfigServiceImpl implements DocumentFileRuntimeConfigService {

    private final DocumentFileConfigStore configStore;

    @Override
    public DocumentFileRuntimeConfig getCurrent() {
        DocumentFileConfigSnapshot snapshot = configStore.resolve();
        return new DocumentFileRuntimeConfig(
            snapshot.getMaxSizeBytes(),
            snapshot.getAllowedSuffixes(),
            snapshot.getUpdatedBy(),
            snapshot.getUpdatedAt());
    }
}

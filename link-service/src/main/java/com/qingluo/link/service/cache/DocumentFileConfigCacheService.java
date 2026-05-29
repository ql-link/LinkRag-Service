package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import java.util.Optional;

public interface DocumentFileConfigCacheService {

    String CACHE_KEY = "document:file-upload:config";

    Optional<DocumentFileConfigDTO> getConfig();

    void putConfig(DocumentFileConfigDTO config);

    boolean putConfigIfAbsent(DocumentFileConfigDTO config);
}

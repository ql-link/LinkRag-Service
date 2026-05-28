package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import java.util.Optional;

public interface KnowledgeFileConfigCacheService {

    String CACHE_KEY = "knowledge:file-upload:config";

    Optional<KnowledgeFileConfigDTO> getConfig();

    void putConfig(KnowledgeFileConfigDTO config);

    boolean putConfigIfAbsent(KnowledgeFileConfigDTO config);
}

package com.qingluo.link.service.impl.admin;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.request.UpdateDocumentFileConfigRequest;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.observability.log.AuditLog;
import com.qingluo.link.service.AdminDocumentFileConfigService;
import com.qingluo.link.service.config.DocumentFileConfigNormalizer;
import com.qingluo.link.service.config.DocumentFileConfigSnapshot;
import com.qingluo.link.service.config.DocumentFileConfigStore;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.support.DocumentFileConfigReadiness;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDocumentFileConfigServiceImpl implements AdminDocumentFileConfigService {

    private final DocumentFileConfigStore configStore;
    private final DocumentFileProperties properties;
    private final DocumentFileConfigReadiness readiness;

    @Override
    public DocumentFileConfigDTO getCurrentConfig() {
        return toDTO(configStore.resolve());
    }

    @Override
    public DocumentFileConfigDTO updateConfig(Long operatorId, UpdateDocumentFileConfigRequest request) {
        try {
            if (!readiness.isReady()) {
                throw new BusinessException(
                    ErrorCode.DOCUMENT_FILE_CONFIG_UPDATE_FAILED,
                    "实例上传默认配置不一致，暂不可修改");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                ErrorCode.DOCUMENT_FILE_CONFIG_UPDATE_FAILED,
                "Redis 不可用，文档文件上传配置未更新");
        }

        LinkedHashSet<String> suffixes = DocumentFileConfigNormalizer.normalizeAndValidate(
            request == null ? null : request.getMaxSizeBytes(),
            request == null ? null : request.getAllowedSuffixes(),
            properties);
        DocumentFileConfigSnapshot snapshot = new DocumentFileConfigSnapshot(
            request.getMaxSizeBytes(),
            suffixes,
            operatorId,
            LocalDateTime.now());
        try {
            configStore.write(snapshot);
        } catch (RuntimeException ex) {
            throw new BusinessException(
                ErrorCode.DOCUMENT_FILE_CONFIG_UPDATE_FAILED,
                "Redis 写入失败，旧配置继续生效");
        }
        AuditLog.event("DOCUMENT_FILE_CONFIG_UPDATE", "operatorId={}, maxSizeBytes={}, allowedSuffixes={}",
            operatorId, snapshot.getMaxSizeBytes(), snapshot.getAllowedSuffixes());
        return toDTO(snapshot);
    }

    private DocumentFileConfigDTO toDTO(DocumentFileConfigSnapshot snapshot) {
        return new DocumentFileConfigDTO(
            snapshot.getMaxSizeBytes(),
            List.copyOf(snapshot.getAllowedSuffixes()),
            snapshot.getUpdatedBy(),
            snapshot.getUpdatedAt());
    }
}

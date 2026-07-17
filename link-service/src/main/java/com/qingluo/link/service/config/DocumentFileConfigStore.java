package com.qingluo.link.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.support.DocumentFileConfigMetrics;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentFileConfigStore {

    public static final String CONFIG_KEY = "runtime:document-file:upload-config";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentFileProperties properties;
    private final DocumentFileConfigMetrics metrics;
    private final AtomicReference<DocumentFileConfigSnapshot> lastValid = new AtomicReference<>();

    public DocumentFileConfigSnapshot resolve() {
        try {
            Object raw = redisTemplate.opsForValue().get(CONFIG_KEY);
            if (raw == null) {
                return defaultSnapshot();
            }
            DocumentFileConfigSnapshot snapshot = raw instanceof DocumentFileConfigSnapshot value
                ? value
                : objectMapper.convertValue(raw, DocumentFileConfigSnapshot.class);
            validateStored(snapshot);
            lastValid.set(copy(snapshot));
            return copy(snapshot);
        } catch (RuntimeException ex) {
            String reason = isDataProblem(ex) ? "corrupted" : "redis_unavailable";
            metrics.fallback(reason);
            DocumentFileConfigSnapshot fallback = lastValid.get();
            log.warn("Document upload config fallback reason={}, hasLastValid={}, error={}",
                reason, fallback != null, ex.getMessage());
            return fallback != null ? copy(fallback) : defaultSnapshot();
        }
    }

    public void write(DocumentFileConfigSnapshot snapshot) {
        redisTemplate.opsForValue().set(CONFIG_KEY, snapshot);
        lastValid.set(copy(snapshot));
    }

    public DocumentFileConfigSnapshot lastValid() {
        DocumentFileConfigSnapshot snapshot = lastValid.get();
        return snapshot == null ? null : copy(snapshot);
    }

    public DocumentFileConfigSnapshot defaultSnapshot() {
        LinkedHashSet<String> suffixes = DocumentFileConfigNormalizer.normalizeOrFallback(
            List.copyOf(properties.getAllowedSuffixes()), properties.getAllowedSuffixes());
        return new DocumentFileConfigSnapshot(properties.getMaxSizeBytes(), suffixes, null, null);
    }

    private void validateStored(DocumentFileConfigSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot is null");
        }
        DocumentFileConfigNormalizer.normalizeAndValidate(
            snapshot.getMaxSizeBytes(),
            snapshot.getAllowedSuffixes() == null ? null : List.copyOf(snapshot.getAllowedSuffixes()),
            properties);
    }

    private boolean isDataProblem(RuntimeException ex) {
        return ex instanceof IllegalArgumentException
            || ex instanceof BusinessException businessException
            && businessException.getCode() == ErrorCode.DOCUMENT_FILE_CONFIG_INVALID.getCode();
    }

    private DocumentFileConfigSnapshot copy(DocumentFileConfigSnapshot source) {
        return new DocumentFileConfigSnapshot(
            source.getMaxSizeBytes(),
            source.getAllowedSuffixes() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(source.getAllowedSuffixes()),
            source.getUpdatedBy(),
            source.getUpdatedAt());
    }
}

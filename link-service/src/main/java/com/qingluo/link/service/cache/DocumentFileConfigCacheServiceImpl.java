package com.qingluo.link.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.model.dto.response.DocumentFileConfigDTO;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentFileConfigCacheServiceImpl implements DocumentFileConfigCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<DocumentFileConfigDTO> getConfig() {
        try {
            Object value = redisTemplate.opsForValue().get(CACHE_KEY);
            if (value == null) {
                return Optional.empty();
            }
            if (value instanceof DocumentFileConfigDTO dto) {
                return Optional.of(dto);
            }
            if (value instanceof String rawJson) {
                return Optional.of(objectMapper.readValue(rawJson, DocumentFileConfigDTO.class));
            }
            return Optional.of(objectMapper.convertValue(value, DocumentFileConfigDTO.class));
        } catch (Exception ex) {
            log.warn("Read document file upload config from Redis failed; fallback to default, key={}, error={}: {}",
                CACHE_KEY, ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putConfig(DocumentFileConfigDTO config) {
        String rawJson = writeJson(config);
        redisTemplate.opsForValue().set(CACHE_KEY, rawJson);
    }

    @Override
    public boolean putConfigIfAbsent(DocumentFileConfigDTO config) {
        String rawJson = writeJson(config);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(CACHE_KEY, rawJson));
    }

    private String writeJson(DocumentFileConfigDTO config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Serialize document file upload config failed", ex);
        }
    }
}

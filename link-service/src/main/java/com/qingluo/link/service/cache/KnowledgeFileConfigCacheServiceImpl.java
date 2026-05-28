package com.qingluo.link.service.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.model.dto.response.KnowledgeFileConfigDTO;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeFileConfigCacheServiceImpl implements KnowledgeFileConfigCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<KnowledgeFileConfigDTO> getConfig() {
        try {
            Object value = redisTemplate.opsForValue().get(CACHE_KEY);
            if (value == null) {
                return Optional.empty();
            }
            if (value instanceof KnowledgeFileConfigDTO dto) {
                return Optional.of(dto);
            }
            if (value instanceof String rawJson) {
                return Optional.of(objectMapper.readValue(rawJson, KnowledgeFileConfigDTO.class));
            }
            return Optional.of(objectMapper.convertValue(value, KnowledgeFileConfigDTO.class));
        } catch (Exception ex) {
            log.warn("Read knowledge file upload config from Redis failed; fallback to default, key={}, error={}: {}",
                CACHE_KEY, ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putConfig(KnowledgeFileConfigDTO config) {
        String rawJson = writeJson(config);
        redisTemplate.opsForValue().set(CACHE_KEY, rawJson);
    }

    @Override
    public boolean putConfigIfAbsent(KnowledgeFileConfigDTO config) {
        String rawJson = writeJson(config);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(CACHE_KEY, rawJson));
    }

    private String writeJson(KnowledgeFileConfigDTO config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Serialize knowledge file upload config failed", ex);
        }
    }
}

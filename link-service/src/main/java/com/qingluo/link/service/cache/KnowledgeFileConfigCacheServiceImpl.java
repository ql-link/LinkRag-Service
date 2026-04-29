package com.qingluo.link.service.cache;

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
            if (value instanceof String rawJson) {
                return Optional.of(objectMapper.readValue(rawJson, KnowledgeFileConfigDTO.class));
            }
            if (value instanceof KnowledgeFileConfigDTO dto) {
                return Optional.of(dto);
            }
            return Optional.of(objectMapper.convertValue(value, KnowledgeFileConfigDTO.class));
        } catch (Exception e) {
            log.warn("Read knowledge file upload config from Redis failed, key={}", CACHE_KEY, e);
            return Optional.empty();
        }
    }

    @Override
    public void putConfig(KnowledgeFileConfigDTO config) {
        try {
            // 该配置是运行时覆盖值，不设置 TTL；Redis 不可用时由上层返回配置保存失败。
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(config));
        } catch (RuntimeException e) {
            log.error("Write knowledge file upload config to Redis failed, key={}", CACHE_KEY, e);
            throw e;
        } catch (Exception e) {
            log.error("Serialize knowledge file upload config failed, key={}", CACHE_KEY, e);
            throw new IllegalStateException("Serialize knowledge file upload config failed", e);
        }
    }
}

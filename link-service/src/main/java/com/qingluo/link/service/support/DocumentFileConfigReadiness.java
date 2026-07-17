package com.qingluo.link.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentFileConfigReadiness {

    public static final String FINGERPRINT_KEY = "runtime:document-file:default-fingerprint";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DocumentFileProperties properties;
    private final ObjectMapper objectMapper;

    public boolean isReady() {
        String current = fingerprint();
        Boolean created = redisTemplate.opsForValue().setIfAbsent(FINGERPRINT_KEY, current);
        if (Boolean.TRUE.equals(created)) {
            return true;
        }
        Object existing = redisTemplate.opsForValue().get(FINGERPRINT_KEY);
        return current.equals(String.valueOf(existing));
    }

    public String fingerprint() {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(Map.of(
                "maxSizeBytes", properties.getMaxSizeBytes(),
                "hardMaxSizeBytes", properties.getHardMaxSizeBytes(),
                "allowedSuffixes", List.copyOf(properties.getAllowedSuffixes()).stream().sorted().toList()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize document defaults", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash document defaults", ex);
        }
    }
}

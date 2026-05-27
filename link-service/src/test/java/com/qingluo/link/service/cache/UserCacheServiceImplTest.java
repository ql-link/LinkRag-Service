package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserCacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CacheConsistencyService cacheConsistencyService;

    @Mock
    private CacheReadProtectionService cacheReadProtectionService;

    @InjectMocks
    private UserCacheServiceImpl userCacheService;

    @Test
    @DisplayName("Should_WriteToRedisWithTtl_When_Put")
    void Should_WriteToRedisWithTtl_When_Put() {
        UserProfileDTO dto = buildDto(1L, "alice", "USER");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        userCacheService.put(1L, dto);

        verify(valueOperations).set(eq("user:info:1"), eq(dto), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    @DisplayName("Should_Continue_When_PutCacheFails")
    void Should_Continue_When_PutCacheFails() {
        UserProfileDTO dto = buildDto(1L, "alice", "USER");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new RuntimeException("redis timeout")).given(valueOperations)
                .set(eq("user:info:1"), eq(dto), eq(7L), eq(TimeUnit.DAYS));

        assertThatCode(() -> userCacheService.put(1L, dto)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should_ReturnDto_When_CacheHit")
    void Should_ReturnDto_When_CacheHit() {
        UserProfileDTO dto = buildDto(1L, "alice", "USER");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("user:info:1")).willReturn(dto);

        UserProfileDTO result = userCacheService.get(1L);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("Should_ReturnNull_When_CacheMiss")
    void Should_ReturnNull_When_CacheMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("user:info:99")).willReturn(null);

        UserProfileDTO result = userCacheService.get(99L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should_ReturnNull_When_CacheReadFails")
    void Should_ReturnNull_When_CacheReadFails() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("user:info:1")).willThrow(new RuntimeException("redis timeout"));

        UserProfileDTO result = userCacheService.get(1L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should_DelegateToReadProtection_When_GetOrLoad")
    void Should_DelegateToReadProtection_When_GetOrLoad() {
        UserProfileDTO dto = buildDto(1L, "alice", "USER");
        @SuppressWarnings("unchecked")
        Supplier<UserProfileDTO> loader = org.mockito.Mockito.mock(Supplier.class);
        given(cacheReadProtectionService.getOrLoad(
                eq("user:info:1"),
                eq(UserProfileDTO.class),
                eq(7L),
                eq(TimeUnit.DAYS),
                any()
        )).willReturn(dto);

        UserProfileDTO result = userCacheService.getOrLoad(1L, loader);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("Should_FallbackToLoader_When_GetOrLoadCacheFails")
    void Should_FallbackToLoader_When_GetOrLoadCacheFails() {
        UserProfileDTO dto = buildDto(1L, "alice", "USER");
        @SuppressWarnings("unchecked")
        Supplier<UserProfileDTO> loader = org.mockito.Mockito.mock(Supplier.class);
        given(cacheReadProtectionService.getOrLoad(
                eq("user:info:1"),
                eq(UserProfileDTO.class),
                eq(7L),
                eq(TimeUnit.DAYS),
                any()
        )).willThrow(new RuntimeException("redis timeout"));
        given(loader.get()).willReturn(dto);

        UserProfileDTO result = userCacheService.getOrLoad(1L, loader);

        assertThat(result).isEqualTo(dto);
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("Should_ReturnLoadedValue_When_BackfillFailsAfterDatabaseLoad")
    void Should_ReturnLoadedValue_When_BackfillFailsAfterDatabaseLoad() {
        UserProfileDTO dto = buildDto(1L, "alice", "USER");
        @SuppressWarnings("unchecked")
        Supplier<UserProfileDTO> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willReturn(dto);
        given(cacheReadProtectionService.getOrLoad(
                eq("user:info:1"),
                eq(UserProfileDTO.class),
                eq(7L),
                eq(TimeUnit.DAYS),
                any()
        )).willAnswer(invocation -> {
            Supplier<UserProfileDTO> trackedLoader = invocation.getArgument(4);
            trackedLoader.get();
            throw new RuntimeException("redis write timeout");
        });

        UserProfileDTO result = userCacheService.getOrLoad(1L, loader);

        assertThat(result).isEqualTo(dto);
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("Should_NotRetryLoader_When_DatabaseLoadFails")
    void Should_NotRetryLoader_When_DatabaseLoadFails() {
        @SuppressWarnings("unchecked")
        Supplier<UserProfileDTO> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willThrow(new RuntimeException("database timeout"));
        given(cacheReadProtectionService.getOrLoad(
                eq("user:info:1"),
                eq(UserProfileDTO.class),
                eq(7L),
                eq(TimeUnit.DAYS),
                any()
        )).willAnswer(invocation -> {
            Supplier<UserProfileDTO> trackedLoader = invocation.getArgument(4);
            return trackedLoader.get();
        });

        assertThatThrownBy(() -> userCacheService.getOrLoad(1L, loader))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database timeout");
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("Should_CallCacheConsistency_When_Evict")
    void Should_CallCacheConsistency_When_Evict() {
        userCacheService.evict(1L);

        verify(cacheConsistencyService).evict(CacheEvictTarget.USER, 1L);
    }

    @Test
    @DisplayName("Should_PropagateFailure_When_ConsistencyEvictionFails")
    void Should_PropagateFailure_When_ConsistencyEvictionFails() {
        willThrow(new RuntimeException("delete failed")).given(cacheConsistencyService)
                .evict(CacheEvictTarget.USER, 1L);

        assertThatThrownBy(() -> userCacheService.evict(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("delete failed");
    }

    private UserProfileDTO buildDto(Long id, String username, String role) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(id);
        dto.setUsername(username);
        dto.setRole(role);
        dto.setStatus(1);
        return dto;
    }
}

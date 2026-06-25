package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLLMConfigCacheServiceImplTest {

    @Mock
    private CacheReadProtectionService cacheReadProtectionService;

    @InjectMocks
    private UserLLMConfigCacheServiceImpl cacheService;

    @Test
    @DisplayName("读取用户 LLM 配置时委托读保护并使用 60 分钟 TTL")
    void getOrLoadAll_delegatesToReadProtection() {
        UserLLMConfigSnapshot snapshot = new UserLLMConfigSnapshot(List.of(dto("gpt-4o")));
        @SuppressWarnings("unchecked")
        Supplier<List<UserLLMConfigDTO>> loader = org.mockito.Mockito.mock(Supplier.class);
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:u_cfg:7"),
                eq(UserLLMConfigSnapshot.class),
                eq(60L),
                eq(TimeUnit.MINUTES),
                any()
        )).willReturn(snapshot);

        List<UserLLMConfigDTO> result = cacheService.getOrLoadAll(7L, loader);

        assertThat(result).extracting(UserLLMConfigDTO::getModelName).containsExactly("gpt-4o");
    }

    @Test
    @DisplayName("缓存读失败且未开始回源时降级直接查库")
    void getOrLoadAll_fallbacksToLoader_whenCacheReadFailsBeforeLoad() {
        @SuppressWarnings("unchecked")
        Supplier<List<UserLLMConfigDTO>> loader = org.mockito.Mockito.mock(Supplier.class);
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:u_cfg:7"),
                eq(UserLLMConfigSnapshot.class),
                eq(60L),
                eq(TimeUnit.MINUTES),
                any()
        )).willThrow(new RuntimeException("redis timeout"));
        given(loader.get()).willReturn(List.of(dto("gpt-4o")));

        List<UserLLMConfigDTO> result = cacheService.getOrLoadAll(7L, loader);

        assertThat(result).extracting(UserLLMConfigDTO::getModelName).containsExactly("gpt-4o");
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("回源成功但回填失败时返回已加载值")
    void getOrLoadAll_returnsLoadedValue_whenBackfillFailsAfterLoad() {
        @SuppressWarnings("unchecked")
        Supplier<List<UserLLMConfigDTO>> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willReturn(List.of(dto("gpt-4o")));
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:u_cfg:7"),
                eq(UserLLMConfigSnapshot.class),
                eq(60L),
                eq(TimeUnit.MINUTES),
                any()
        )).willAnswer(invocation -> {
            Supplier<UserLLMConfigSnapshot> trackedLoader = invocation.getArgument(4);
            trackedLoader.get();
            throw new RuntimeException("redis write timeout");
        });

        List<UserLLMConfigDTO> result = cacheService.getOrLoadAll(7L, loader);

        assertThat(result).extracting(UserLLMConfigDTO::getModelName).containsExactly("gpt-4o");
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("数据库回源失败时不重复执行 loader")
    void getOrLoadAll_doesNotRetryLoader_whenDatabaseLoadFails() {
        @SuppressWarnings("unchecked")
        Supplier<List<UserLLMConfigDTO>> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willThrow(new RuntimeException("database timeout"));
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:u_cfg:7"),
                eq(UserLLMConfigSnapshot.class),
                eq(60L),
                eq(TimeUnit.MINUTES),
                any()
        )).willAnswer(invocation -> {
            Supplier<UserLLMConfigSnapshot> trackedLoader = invocation.getArgument(4);
            return trackedLoader.get();
        });

        assertThatThrownBy(() -> cacheService.getOrLoadAll(7L, loader))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database timeout");
        verify(loader, times(1)).get();
    }

    private UserLLMConfigDTO dto(String modelName) {
        UserLLMConfigDTO dto = new UserLLMConfigDTO();
        dto.setModelName(modelName);
        return dto;
    }
}

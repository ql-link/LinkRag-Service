package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link ProviderCatalogCacheServiceImpl} 单元测试。
 *
 * <p>覆盖读保护委托与缓存不可用时的降级语义：命中不回源、未命中回源、
 * 回填失败返回已加载值、回源失败上抛且不重复执行 loader。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProviderCatalogCacheServiceImplTest {

    @Mock
    private CacheReadProtectionService cacheReadProtectionService;

    @InjectMocks
    private ProviderCatalogCacheServiceImpl cacheService;

    @Test
    @DisplayName("命中缓存时委托读保护返回快照且不触发 loader")
    void getOrLoad_returnsCachedSnapshot_withoutInvokingLoader() {
        ProviderCatalogSnapshot cached = snapshot();
        @SuppressWarnings("unchecked")
        Supplier<ProviderCatalogSnapshot> loader = org.mockito.Mockito.mock(Supplier.class);
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:pvd:catalog"),
                eq(ProviderCatalogSnapshot.class),
                eq(60L),
                eq(TimeUnit.MINUTES),
                any()
        )).willReturn(cached);

        ProviderCatalogSnapshot result = cacheService.getOrLoad(loader);

        assertThat(result).isSameAs(cached);
        verify(loader, never()).get();
    }

    @Test
    @DisplayName("未命中时执行 loader 回源")
    void getOrLoad_invokesLoader_onCacheMiss() {
        ProviderCatalogSnapshot loaded = snapshot();
        @SuppressWarnings("unchecked")
        Supplier<ProviderCatalogSnapshot> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willReturn(loaded);
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:pvd:catalog"), eq(ProviderCatalogSnapshot.class), eq(60L), eq(TimeUnit.MINUTES), any()
        )).willAnswer(invocation -> {
            Supplier<ProviderCatalogSnapshot> trackedLoader = invocation.getArgument(4);
            return trackedLoader.get();
        });

        ProviderCatalogSnapshot result = cacheService.getOrLoad(loader);

        assertThat(result).isSameAs(loaded);
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("回源已完成但回填失败时返回已加载快照")
    void getOrLoad_returnsLoaded_whenBackfillFailsAfterLoad() {
        ProviderCatalogSnapshot loaded = snapshot();
        @SuppressWarnings("unchecked")
        Supplier<ProviderCatalogSnapshot> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willReturn(loaded);
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:pvd:catalog"), eq(ProviderCatalogSnapshot.class), eq(60L), eq(TimeUnit.MINUTES), any()
        )).willAnswer(invocation -> {
            Supplier<ProviderCatalogSnapshot> trackedLoader = invocation.getArgument(4);
            trackedLoader.get();
            throw new RuntimeException("redis write timeout");
        });

        ProviderCatalogSnapshot result = cacheService.getOrLoad(loader);

        assertThat(result).isSameAs(loaded);
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("回源本身失败时上抛且不重复执行 loader")
    void getOrLoad_propagates_whenLoadFails() {
        @SuppressWarnings("unchecked")
        Supplier<ProviderCatalogSnapshot> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willThrow(new RuntimeException("database timeout"));
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:pvd:catalog"), eq(ProviderCatalogSnapshot.class), eq(60L), eq(TimeUnit.MINUTES), any()
        )).willAnswer(invocation -> {
            Supplier<ProviderCatalogSnapshot> trackedLoader = invocation.getArgument(4);
            return trackedLoader.get();
        });

        assertThatThrownBy(() -> cacheService.getOrLoad(loader))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database timeout");
        verify(loader, times(1)).get();
    }

    @Test
    @DisplayName("读穿透异常且 loader 未启动时降级回源")
    void getOrLoad_fallsBackToLoader_whenReadThroughFailsBeforeLoad() {
        ProviderCatalogSnapshot loaded = snapshot();
        @SuppressWarnings("unchecked")
        Supplier<ProviderCatalogSnapshot> loader = org.mockito.Mockito.mock(Supplier.class);
        given(loader.get()).willReturn(loaded);
        given(cacheReadProtectionService.getOrLoad(
                eq("llm:pvd:catalog"), eq(ProviderCatalogSnapshot.class), eq(60L), eq(TimeUnit.MINUTES), any()
        )).willThrow(new RuntimeException("redis timeout"));

        ProviderCatalogSnapshot result = cacheService.getOrLoad(loader);

        assertThat(result).isSameAs(loaded);
        verify(loader, times(1)).get();
    }

    private ProviderCatalogSnapshot snapshot() {
        SystemProvider provider = new SystemProvider();
        provider.setId(5L);
        provider.setProviderType("openai");
        provider.setProviderName("OpenAI");
        provider.setIsActive(true);
        ProviderModel model = new ProviderModel();
        model.setProviderId(5L);
        model.setModelName("gpt-4o");
        model.setCapability("CHAT");
        model.setIsActive(true);
        return new ProviderCatalogSnapshot(List.of(provider), List.of(model));
    }
}

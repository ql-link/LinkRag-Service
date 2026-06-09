package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.response.ProviderModelDTO;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.cache.ProviderCatalogCacheService;
import com.qingluo.link.service.cache.ProviderCatalogSnapshot;
import com.qingluo.link.service.impl.llm.SystemProviderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link SystemProviderServiceImpl} 单元测试。
 *
 * <p>承接 acceptance 一类（按能力聚合目录、空厂商过滤），并守护两条性能治理：
 * 命中厂商目录缓存时不查库；未命中回源时模型查询只触发一次批量请求（消除 N+1）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SystemProviderServiceImplTest {

    @Mock
    private SystemProviderMapper systemProviderMapper;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private ProviderModelService providerModelService;
    @Mock
    private ProviderCatalogCacheService providerCatalogCacheService;

    @InjectMocks
    private SystemProviderServiceImpl service;

    // ---- 内存聚合：数据源来自缓存快照，命中时不应触达数据库 ----

    @Test
    @DisplayName("一·按能力查询聚合返回该能力下的模型集合（命中缓存不查库）")
    void getActiveProviderModels_aggregatesByCapability() {
        givenCacheHit(new ProviderCatalogSnapshot(
                List.of(provider(5L, "openai")),
                List.of(pm(5L, "gpt-4o", "CHAT"), pm(5L, "gpt-4o-mini", "CHAT"))));

        List<ProviderModelDTO> result = service.getActiveProviderModels("CHAT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModels()).extracting("modelName")
                .containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini");
        // 命中缓存：不查厂商表、不查模型表
        verifyNoInteractions(systemProviderMapper, providerModelService);
    }

    @Test
    @DisplayName("一·过滤后无可选模型的厂商不返回")
    void getActiveProviderModels_dropsEmptyProvider() {
        givenCacheHit(new ProviderCatalogSnapshot(
                List.of(provider(5L, "openai"), provider(6L, "deepseek")),
                List.of(pm(5L, "text-embedding-3", "EMBEDDING"))));

        List<ProviderModelDTO> result = service.getActiveProviderModels("EMBEDDING");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProviderType()).isEqualTo("openai");
    }

    @Test
    @DisplayName("一·一个模型的多种能力聚合为能力列表")
    void getActiveProviderModels_groupsMultiCapability() {
        givenCacheHit(new ProviderCatalogSnapshot(
                List.of(provider(5L, "openai")),
                List.of(pm(5L, "gpt-4o", "CHAT"), pm(5L, "gpt-4o", "VISION"), pm(5L, "gpt-4o", "OCR"))));

        List<ProviderModelDTO> result = service.getActiveProviderModels(null);

        assertThat(result.get(0).getModels()).hasSize(1);
        assertThat(result.get(0).getModels().get(0).getCapabilities())
                .containsExactlyInAnyOrder("CHAT", "VISION", "OCR");
    }

    @Test
    @DisplayName("一·capability 过滤在内存完成：全量快照按能力筛选并丢掉不含该能力的厂商")
    void getActiveProviderModels_filtersCapabilityInMemory() {
        // 缓存的是全量（含 CHAT 与 EMBEDDING），按 EMBEDDING 查时只应留 openai 的 embedding 模型
        givenCacheHit(new ProviderCatalogSnapshot(
                List.of(provider(5L, "openai"), provider(6L, "deepseek")),
                List.of(pm(5L, "gpt-4o", "CHAT"),
                        pm(5L, "text-embedding-3", "EMBEDDING"),
                        pm(6L, "deepseek-chat", "CHAT"))));

        List<ProviderModelDTO> result = service.getActiveProviderModels("EMBEDDING");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProviderType()).isEqualTo("openai");
        assertThat(result.get(0).getModels()).extracting("modelName").containsExactly("text-embedding-3");
        verifyNoInteractions(systemProviderMapper, providerModelService);
    }

    // ---- 回源：缓存未命中时执行 loader，守护 N+1 与空厂商短路 ----

    @Test
    @DisplayName("一·多厂商目录回源只触发一次批量模型查询（消除 N+1）")
    void getActiveProviderModels_batchesModelQueryOnce() {
        givenCacheMissExecutingLoader();
        given(systemProviderMapper.selectList(any()))
                .willReturn(List.of(provider(5L, "openai"), provider(6L, "deepseek"), provider(7L, "claude")));
        given(providerModelService.listActiveModelsByProviderIds(any(), any()))
                .willReturn(List.of(pm(5L, "gpt-4o", "CHAT"), pm(6L, "deepseek-chat", "CHAT"), pm(7L, "claude-3", "CHAT")));

        List<ProviderModelDTO> result = service.getActiveProviderModels("CHAT");

        assertThat(result).hasSize(3);
        // 回源构建快照时按全量查（capability 传 null），过滤在内存做；批量查询只发一次
        verify(providerModelService, times(1)).listActiveModelsByProviderIds(eq(List.of(5L, 6L, 7L)), isNull());
        verify(providerModelService, never()).listActiveModels(any(), any());
    }

    @Test
    @DisplayName("一·无启用厂商时直接返回空且不查询模型")
    void getActiveProviderModels_noProvidersShortCircuits() {
        givenCacheMissExecutingLoader();
        given(systemProviderMapper.selectList(any())).willReturn(List.of());

        List<ProviderModelDTO> result = service.getActiveProviderModels(null);

        assertThat(result).isEmpty();
        verify(providerModelService, never()).listActiveModelsByProviderIds(any(), any());
    }

    /** 缓存命中：直接返回快照，loader 不执行。 */
    private void givenCacheHit(ProviderCatalogSnapshot snapshot) {
        given(providerCatalogCacheService.getOrLoad(any())).willReturn(snapshot);
    }

    /** 缓存未命中：真正回调 loader 走数据库回源。 */
    @SuppressWarnings("unchecked")
    private void givenCacheMissExecutingLoader() {
        given(providerCatalogCacheService.getOrLoad(any())).willAnswer(invocation ->
                ((Supplier<ProviderCatalogSnapshot>) invocation.getArgument(0)).get());
    }

    private SystemProvider provider(Long id, String type) {
        SystemProvider provider = new SystemProvider();
        provider.setId(id);
        provider.setProviderType(type);
        provider.setProviderName(type);
        provider.setIsActive(true);
        provider.setPriority(50);
        return provider;
    }

    private ProviderModel pm(Long providerId, String model, String capability) {
        ProviderModel m = new ProviderModel();
        m.setProviderId(providerId);
        m.setModelName(model);
        m.setCapability(capability);
        m.setIsActive(true);
        return m;
    }
}

package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.entity.ProviderModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ProviderCatalogCacheServiceImpl} 单元测试。
 *
 * <p>覆盖「索引 + 按厂商分片」编排：索引/分片全命中组装、空厂商短路、分片缺失回源分组，
 * 以及容错分层——索引回填失败返回已加载值（不降级）、索引读故障与分片读故障整体降级查库。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProviderCatalogCacheServiceImplTest {

    private static final String INDEX_KEY = "llm:pvd:catalog:index";

    @Mock
    private CacheReadProtectionService cacheReadProtectionService;

    @InjectMocks
    private ProviderCatalogCacheServiceImpl cacheService;

    @Test
    @DisplayName("索引与分片全命中：组装快照，providers 来自索引、models 来自分片")
    void getOrLoad_assemblesFromIndexAndShards_onFullHit() {
        givenIndex(new ProviderCatalogIndex(List.of(ref(5L, "openai"))));
        givenShards(Map.of("llm:pvd:catalog:models:openai",
                new ProviderModelShard(List.of(pm(5L, "gpt-4o", "CHAT")))));

        ProviderCatalogSnapshot snapshot = cacheService.getOrLoad(
                () -> List.of(ref(5L, "openai")),
                ids -> List.of());

        assertThat(snapshot.getProviders()).extracting(ProviderRef::getProviderType).containsExactly("openai");
        assertThat(snapshot.getModels()).extracting(ProviderModel::getModelName).containsExactly("gpt-4o");
    }

    @Test
    @DisplayName("索引为空：直接返回空快照且不读分片")
    void getOrLoad_returnsEmpty_whenNoProviders() {
        givenIndex(new ProviderCatalogIndex(List.of()));

        ProviderCatalogSnapshot snapshot = cacheService.getOrLoad(List::of, ids -> List.of());

        assertThat(snapshot.getProviders()).isEmpty();
        assertThat(snapshot.getModels()).isEmpty();
        verify(cacheReadProtectionService, never()).getOrLoadBatch(any(), any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("分片缺失：按 type→id 回源并分组成各厂商分片")
    void getOrLoad_loadsMissingShardsFromModelsLoader() {
        givenIndex(new ProviderCatalogIndex(List.of(ref(5L, "openai"), ref(6L, "deepseek"))));
        // 模拟全部分片缺失：getOrLoadBatch 真正回调 batchLoader 回源
        given(cacheReadProtectionService.getOrLoadBatch(
                any(), eq(ProviderModelShard.class), eq(60L), eq(TimeUnit.MINUTES), any()))
                .willAnswer(inv -> {
                    List<String> keys = inv.getArgument(0);
                    Function<List<String>, Map<String, ProviderModelShard>> loader = inv.getArgument(4);
                    return loader.apply(keys);
                });

        ProviderCatalogSnapshot snapshot = cacheService.getOrLoad(
                () -> List.of(ref(5L, "openai"), ref(6L, "deepseek")),
                ids -> List.of(pm(5L, "gpt-4o", "CHAT"), pm(6L, "deepseek-chat", "CHAT")));

        assertThat(snapshot.getModels()).extracting(ProviderModel::getModelName)
                .containsExactlyInAnyOrder("gpt-4o", "deepseek-chat");
    }

    @Test
    @DisplayName("索引回填失败但回源已完成：返回已加载索引，继续读分片，不整体降级")
    void getOrLoad_returnsLoadedIndex_whenIndexBackfillFails() {
        given(cacheReadProtectionService.getOrLoad(
                eq(INDEX_KEY), eq(ProviderCatalogIndex.class), eq(60L), eq(TimeUnit.MINUTES), any()))
                .willAnswer(inv -> {
                    Supplier<ProviderCatalogIndex> tracked = inv.getArgument(4);
                    tracked.get();                                 // 回源完成（loadCompleted=true）
                    throw new RuntimeException("redis write timeout"); // 回填失败
                });
        givenShards(Map.of("llm:pvd:catalog:models:openai",
                new ProviderModelShard(List.of(pm(5L, "gpt-4o", "CHAT")))));

        ProviderCatalogSnapshot snapshot = cacheService.getOrLoad(
                () -> List.of(ref(5L, "openai")),
                ids -> List.of());

        assertThat(snapshot.getProviders()).extracting(ProviderRef::getProviderType).containsExactly("openai");
        assertThat(snapshot.getModels()).extracting(ProviderModel::getModelName).containsExactly("gpt-4o");
    }

    @Test
    @DisplayName("索引读故障（loader 未启动）：整体降级全量查库，不读分片")
    void getOrLoad_fallsBackToDb_whenIndexReadFailsBeforeLoad() {
        given(cacheReadProtectionService.getOrLoad(
                eq(INDEX_KEY), eq(ProviderCatalogIndex.class), eq(60L), eq(TimeUnit.MINUTES), any()))
                .willThrow(new RuntimeException("redis down"));

        ProviderCatalogSnapshot snapshot = cacheService.getOrLoad(
                () -> List.of(ref(5L, "openai")),
                ids -> List.of(pm(5L, "gpt-4o", "CHAT")));

        assertThat(snapshot.getProviders()).extracting(ProviderRef::getProviderType).containsExactly("openai");
        assertThat(snapshot.getModels()).extracting(ProviderModel::getModelName).containsExactly("gpt-4o");
        verify(cacheReadProtectionService, never()).getOrLoadBatch(any(), any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("分片读故障：整体降级全量查库")
    void getOrLoad_fallsBackToDb_whenShardReadFails() {
        givenIndex(new ProviderCatalogIndex(List.of(ref(5L, "openai"))));
        given(cacheReadProtectionService.getOrLoadBatch(
                any(), eq(ProviderModelShard.class), eq(60L), eq(TimeUnit.MINUTES), any()))
                .willThrow(new RuntimeException("redis mget failed"));

        ProviderCatalogSnapshot snapshot = cacheService.getOrLoad(
                () -> List.of(ref(5L, "openai")),
                ids -> List.of(pm(5L, "gpt-4o", "CHAT")));

        assertThat(snapshot.getModels()).extracting(ProviderModel::getModelName).containsExactly("gpt-4o");
    }

    @Test
    @DisplayName("解析取法：索引命中按 id 得 providerType（只读、不回源）")
    void resolveProviderTypeById_hit() {
        given(cacheReadProtectionService.getIfPresent(INDEX_KEY, ProviderCatalogIndex.class))
                .willReturn(new ProviderCatalogIndex(List.of(ref(3L, "openai"), ref(5L, "claude"))));

        assertThat(cacheService.resolveProviderTypeById(3L)).isEqualTo("openai");
    }

    @Test
    @DisplayName("解析取法：索引未命中（重建态）返回 null")
    void resolveProviderTypeById_indexMiss() {
        given(cacheReadProtectionService.getIfPresent(INDEX_KEY, ProviderCatalogIndex.class))
                .willReturn(null);

        assertThat(cacheService.resolveProviderTypeById(3L)).isNull();
    }

    @Test
    @DisplayName("解析取法：id 不在启用索引（厂商停用/删除）返回 null")
    void resolveProviderTypeById_idNotInIndex() {
        given(cacheReadProtectionService.getIfPresent(INDEX_KEY, ProviderCatalogIndex.class))
                .willReturn(new ProviderCatalogIndex(List.of(ref(5L, "claude"))));

        assertThat(cacheService.resolveProviderTypeById(3L)).isNull();
    }

    @Test
    @DisplayName("解析取法：providerId 为 null 直接返回 null，不读缓存")
    void resolveProviderTypeById_nullId() {
        assertThat(cacheService.resolveProviderTypeById(null)).isNull();
    }

    private void givenIndex(ProviderCatalogIndex index) {
        given(cacheReadProtectionService.getOrLoad(
                eq(INDEX_KEY), eq(ProviderCatalogIndex.class), eq(60L), eq(TimeUnit.MINUTES), any()))
                .willReturn(index);
    }

    private void givenShards(Map<String, ProviderModelShard> shards) {
        given(cacheReadProtectionService.getOrLoadBatch(
                any(), eq(ProviderModelShard.class), eq(60L), eq(TimeUnit.MINUTES), any()))
                .willReturn(shards);
    }

    private ProviderRef ref(Long id, String type) {
        return new ProviderRef(id, type, type, null, 50);
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

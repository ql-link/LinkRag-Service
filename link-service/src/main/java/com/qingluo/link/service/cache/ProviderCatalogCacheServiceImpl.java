package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import com.qingluo.link.model.dto.entity.ProviderModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 厂商目录缓存服务实现。
 *
 * <p>缓存按「索引 + 按厂商分片」组织：索引走单 key 读保护，分片走批量读保护（MGET + 部分回源回填）。
 * 读保护、空值占位与 TTL 抖动复用 {@link CacheReadProtectionService}。</p>
 *
 * <p>容错分层：索引读保留单 key 语义——回源完成但回填失败时返回已加载值（不浪费已查数据）；
 * 其余缓存异常（索引读穿透、分片 MGET 读故障）冒泡到 {@link #getOrLoad} 外层，整体降级为
 * 「全量查库」，不因 Redis 抖动阻断用户侧目录查询；分片回填故障由批量读保护内部吞掉。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderCatalogCacheServiceImpl implements ProviderCatalogCacheService {

    /** 目录为低频字典数据，变更靠 evict 主动失效，TTL 仅作兜底自愈（叠加读保护内置的 TTL 抖动防雪崩）。 */
    static final long TTL_MINUTES = 60L;

    private final CacheReadProtectionService cacheReadProtectionService;

    @Override
    public ProviderCatalogSnapshot getOrLoad(Supplier<List<ProviderRef>> providersLoader,
                                             Function<List<Long>, List<ProviderModel>> modelsLoader) {
        try {
            List<ProviderRef> providers = loadIndex(providersLoader);
            if (providers.isEmpty()) {
                return new ProviderCatalogSnapshot(List.of(), List.of());
            }
            List<ProviderModel> models = loadModels(providers, modelsLoader);
            return new ProviderCatalogSnapshot(providers, models);
        } catch (RuntimeException ex) {
            // 缓存层不可用（索引读穿透 / 分片读故障 / 回源异常）→ 全量降级查库，保证查询可用
            log.warn("Provider catalog cache unavailable, fallback to full database load, error={}: {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return fullLoadFromDatabase(providersLoader, modelsLoader);
        }
    }

    /**
     * 读取厂商索引（单 key 读保护）。回源完成但回填失败时返回已加载值；其余异常交外层降级。
     */
    private List<ProviderRef> loadIndex(Supplier<List<ProviderRef>> providersLoader) {
        AtomicBoolean loadCompleted = new AtomicBoolean(false);
        AtomicReference<ProviderCatalogIndex> loadedValue = new AtomicReference<>();
        Supplier<ProviderCatalogIndex> trackedLoader = () -> {
            ProviderCatalogIndex value = new ProviderCatalogIndex(providersLoader.get());
            loadedValue.set(value);
            loadCompleted.set(true);
            return value;
        };
        ProviderCatalogIndex index;
        try {
            index = cacheReadProtectionService.getOrLoad(
                    INDEX_KEY, ProviderCatalogIndex.class, TTL_MINUTES, TimeUnit.MINUTES, trackedLoader);
        } catch (RuntimeException ex) {
            if (!loadCompleted.get()) {
                throw ex;
            }
            log.warn("Backfill provider catalog index failed after load; return loaded value, error={}: {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            index = loadedValue.get();
        }
        List<ProviderRef> providers = index == null ? null : index.getProviders();
        return providers == null ? List.of() : providers;
    }

    /**
     * 批量读各厂商模型分片，缺失按 type→id 回源分组回填，合并为全量模型列表。
     */
    private List<ProviderModel> loadModels(List<ProviderRef> providers,
                                           Function<List<Long>, List<ProviderModel>> modelsLoader) {
        // 分片 key → 厂商 id（保持厂商顺序）
        Map<String, Long> keyToId = new LinkedHashMap<>();
        for (ProviderRef ref : providers) {
            keyToId.put(MODELS_KEY_PREFIX + ref.getProviderType(), ref.getId());
        }
        List<String> shardKeys = new ArrayList<>(keyToId.keySet());

        Map<String, ProviderModelShard> shards = cacheReadProtectionService.getOrLoadBatch(
                shardKeys, ProviderModelShard.class, TTL_MINUTES, TimeUnit.MINUTES,
                missingKeys -> loadMissingShards(missingKeys, keyToId, modelsLoader));

        List<ProviderModel> models = new ArrayList<>();
        for (String key : shardKeys) {
            ProviderModelShard shard = shards.get(key);
            if (shard != null && shard.getModels() != null) {
                models.addAll(shard.getModels());
            }
        }
        return models;
    }

    /**
     * 回源缺失分片：缺失 key→厂商 id 批量查模型，再按 id→key 分组成各厂商分片。
     * 无模型的厂商不出现在返回 Map 中，由批量读保护写空值占位防穿透。
     */
    private Map<String, ProviderModelShard> loadMissingShards(List<String> missingKeys, Map<String, Long> keyToId,
                                                              Function<List<Long>, List<ProviderModel>> modelsLoader) {
        Map<Long, String> idToKey = new LinkedHashMap<>();
        List<Long> missingIds = new ArrayList<>();
        for (String key : missingKeys) {
            Long id = keyToId.get(key);
            if (id != null) {
                idToKey.put(id, key);
                missingIds.add(id);
            }
        }
        List<ProviderModel> models = modelsLoader.apply(missingIds);
        Map<String, List<ProviderModel>> grouped = new LinkedHashMap<>();
        for (ProviderModel model : models) {
            String key = idToKey.get(model.getProviderId());
            if (key != null) {
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(model);
            }
        }
        Map<String, ProviderModelShard> result = new LinkedHashMap<>();
        grouped.forEach((key, list) -> result.put(key, new ProviderModelShard(list)));
        return result;
    }

    /**
     * 缓存整体不可用时的全量降级：直接查库组装，不经缓存。
     */
    private ProviderCatalogSnapshot fullLoadFromDatabase(Supplier<List<ProviderRef>> providersLoader,
                                                         Function<List<Long>, List<ProviderModel>> modelsLoader) {
        List<ProviderRef> providers = providersLoader.get();
        if (providers == null || providers.isEmpty()) {
            return new ProviderCatalogSnapshot(List.of(), List.of());
        }
        List<Long> ids = providers.stream().map(ProviderRef::getId).toList();
        List<ProviderModel> models = modelsLoader.apply(ids);
        return new ProviderCatalogSnapshot(providers, models);
    }
}

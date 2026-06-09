package com.qingluo.link.service.cache;

import com.qingluo.link.components.redis.service.CacheReadProtectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 厂商目录缓存服务实现。
 *
 * <p>读保护、空值占位与 TTL 抖动复用 {@link CacheReadProtectionService}；缓存不可用时按
 * 「数据库回源优先」降级，不因 Redis 抖动阻断用户侧目录查询。容错语义与 {@code UserCacheServiceImpl} 一致：
 * 回源已完成但回填失败时返回已加载值；回源本身失败时保留原异常且不重复执行 loader。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderCatalogCacheServiceImpl implements ProviderCatalogCacheService {

    /** 目录为低频字典数据，变更靠 evict 主动失效，TTL 仅作兜底自愈（叠加读保护内置的 TTL 抖动防雪崩）。 */
    static final long TTL_MINUTES = 60L;

    private final CacheReadProtectionService cacheReadProtectionService;

    @Override
    public ProviderCatalogSnapshot getOrLoad(Supplier<ProviderCatalogSnapshot> loader) {
        AtomicBoolean loadStarted = new AtomicBoolean(false);
        AtomicBoolean loadCompleted = new AtomicBoolean(false);
        AtomicReference<ProviderCatalogSnapshot> loadedValue = new AtomicReference<>();
        Supplier<ProviderCatalogSnapshot> trackedLoader = () -> {
            loadStarted.set(true);
            ProviderCatalogSnapshot value = loader.get();
            loadedValue.set(value);
            loadCompleted.set(true);
            return value;
        };
        try {
            return cacheReadProtectionService.getOrLoad(
                    CATALOG_CACHE_KEY,
                    ProviderCatalogSnapshot.class,
                    TTL_MINUTES,
                    TimeUnit.MINUTES,
                    trackedLoader
            );
        } catch (RuntimeException ex) {
            if (loadCompleted.get()) {
                log.warn("Backfill provider catalog cache failed after database load; return loaded value, error={}: {}",
                        ex.getClass().getSimpleName(), ex.getMessage());
                return loadedValue.get();
            }
            if (loadStarted.get()) {
                throw ex;
            }
            log.warn("Read-through provider catalog cache failed; fallback to database, error={}: {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return loader.get();
        }
    }
}

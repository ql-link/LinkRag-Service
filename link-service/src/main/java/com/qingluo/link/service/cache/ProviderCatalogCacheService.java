package com.qingluo.link.service.cache;

import java.util.function.Supplier;

/**
 * 用户侧厂商目录缓存服务。
 *
 * <p>key：{@code llm:pvd:catalog}（全量一份），缓存厂商目录原始快照。读未命中时回源数据库并回填；
 * 写路径通过 {@code CacheEvictTarget.SYSTEM_PROVIDER} 的 evict 连带失效本 key（见 CacheKeyRouter）。</p>
 */
public interface ProviderCatalogCacheService {

    /** 用户侧厂商目录全量缓存 key，须与 CacheKeyRouter 中 SYSTEM_PROVIDER 连带删除的 catalog key 保持一致。 */
    String CATALOG_CACHE_KEY = "llm:pvd:catalog";

    /**
     * 读取厂商目录快照：命中缓存直接返回（0 次 DB）；未命中时执行 loader 回源并回填。
     */
    ProviderCatalogSnapshot getOrLoad(Supplier<ProviderCatalogSnapshot> loader);
}

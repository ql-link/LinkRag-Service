package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.entity.ProviderModel;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 用户侧厂商目录缓存服务。
 *
 * <p>缓存按「索引 + 按厂商分片」组织，取代原单一大 key：
 * <ul>
 *   <li>{@code llm:pvd:catalog:index}：启用厂商索引（{@link ProviderCatalogIndex}，轻量引用，priority 倒序）；</li>
 *   <li>{@code llm:pvd:catalog:models:{providerType}}：单厂商模型分片（{@link ProviderModelShard}）。</li>
 * </ul>
 * 读未命中时回源数据库并回填；写路径通过 {@code CacheEvictTarget.SYSTEM_PROVIDER} 的 evict
 * 连带失效对应厂商分片与索引（见 CacheKeyRouter）。</p>
 */
public interface ProviderCatalogCacheService {

    /** 启用厂商索引 key（全量一份）。 */
    String INDEX_KEY = "llm:pvd:catalog:index";

    /** 单厂商模型分片 key 前缀，完整 key 为 {@code MODELS_KEY_PREFIX + providerType}。 */
    String MODELS_KEY_PREFIX = "llm:pvd:catalog:models:";

    /**
     * 读取厂商目录：先读索引拿启用厂商，再批量读各厂商模型分片，缺失回源回填，合并为快照。
     *
     * @param providersLoader 回源启用厂商→轻量引用列表（priority 倒序）
     * @param modelsLoader    按厂商 id 批量回源上架模型：入参=厂商 id 列表，返回=这些厂商的模型（元素含 providerId）
     * @return 组装后的目录快照（providers 来自索引、models 由各分片合并）
     */
    ProviderCatalogSnapshot getOrLoad(Supplier<List<ProviderRef>> providersLoader,
                                      Function<List<Long>, List<ProviderModel>> modelsLoader);
}

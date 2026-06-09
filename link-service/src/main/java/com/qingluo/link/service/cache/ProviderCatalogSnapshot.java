package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户侧厂商目录的全量缓存快照。
 *
 * <p>缓存「厂商 + 模型」的原始数据（全部启用厂商及其全部上架模型，不按 capability 过滤），
 * capability 过滤与 DTO 聚合在缓存命中后于内存完成，避免按 capability 分散成多个 key 难以失效。</p>
 *
 * <p>顶层声明为具体类型，绕开 {@code CacheReadProtectionService.getOrLoad(Class)} 对泛型集合的类型擦除：
 * 直接以 {@code ProviderCatalogSnapshot.class} 反序列化即可按字段声明还原内部两个 List 的元素类型。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCatalogSnapshot {

    /** 全部启用厂商，保留 priority 倒序。 */
    private List<SystemProvider> providers;

    /** 上述厂商的全部上架模型（全 capability，未过滤）。 */
    private List<ProviderModel> models;
}

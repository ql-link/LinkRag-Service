package com.qingluo.link.service.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 厂商目录索引缓存值（key：{@code llm:pvd:catalog:index}）。
 *
 * <p>顶层声明为具体类型，绕开缓存反序列化对 {@code List<ProviderRef>} 的泛型擦除：
 * 直接以 {@code ProviderCatalogIndex.class} 反序列化即可还原内部 List 的元素类型。
 * 列表按 priority 倒序，决定用户侧厂商展示顺序。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCatalogIndex {

    /** 全部启用厂商的轻量引用，priority 倒序。 */
    private List<ProviderRef> providers;
}

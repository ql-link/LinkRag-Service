package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.entity.ProviderModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单厂商模型分片缓存值（key：{@code llm:pvd:catalog:models:{providerType}}）。
 *
 * <p>顶层声明为具体类型，绕开缓存反序列化对 {@code List<ProviderModel>} 的泛型擦除。
 * 装某个厂商的全部上架模型（全 capability 未过滤），capability 过滤在命中后于内存完成。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderModelShard {

    /** 该厂商的全部上架模型（全 capability 未过滤）。 */
    private List<ProviderModel> models;
}

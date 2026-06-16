package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.entity.ProviderModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户侧厂商目录的组装结果（非直接缓存值）。
 *
 * <p>厂商目录缓存已拆分为索引 key（{@link ProviderCatalogIndex}）与按厂商的模型分片
 * （{@link ProviderModelShard}）。本类是缓存服务读取并合并后返回给上层的聚合视图：
 * providers 来自索引、models 由各分片合并而成；capability 过滤与 DTO 聚合在上层于内存完成。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCatalogSnapshot {

    /** 全部启用厂商的轻量引用，priority 倒序。 */
    private List<ProviderRef> providers;

    /** 上述厂商的全部上架模型（全 capability，未过滤），由各分片合并而成。 */
    private List<ProviderModel> models;
}

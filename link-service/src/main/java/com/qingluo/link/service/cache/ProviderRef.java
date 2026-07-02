package com.qingluo.link.service.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 厂商目录索引中的轻量厂商引用。
 *
 * <p>仅保留用户侧目录展示与回源所需字段：type/name 用于组装 DTO，id 用于按厂商回源模型，
 * priority 用于展示排序。不含 apiBaseUrl 等重字段，使索引 key 体积足够小。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRef {

    /** 厂商 ID，用于按厂商批量回源模型。 */
    private Long id;

    /** 厂商类型，分片 key 后缀与 evict 口径。 */
    private String providerType;

    /** 厂商名称，用户侧展示。 */
    private String providerName;

    /** 厂商图标 URL，用户侧展示。 */
    private String iconUrl;

    /** 优先级，priority 倒序决定展示顺序。 */
    private Integer priority;
}

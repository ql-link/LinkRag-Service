package com.qingluo.link.service.recall;

import java.util.List;

/**
 * datasetIds 校验/展开的结果。
 *
 * @param datasetIds     已校验（非空入参）或展开（空入参→本人全部库）后的 dataset id 列表；
 *                       {@code emptyOwnership=true} 时为空列表
 * @param emptyOwnership true 表示当前用户名下没有任何数据集（空入参展开后为空）——此时直接返回空 hits、不调 Python
 */
public record ResolvedScope(List<Long> datasetIds, boolean emptyOwnership) {

    public static ResolvedScope of(List<Long> datasetIds) {
        return new ResolvedScope(datasetIds, false);
    }

    public static ResolvedScope empty() {
        return new ResolvedScope(List.of(), true);
    }
}

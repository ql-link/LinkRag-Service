package com.qingluo.link.service.recall;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.enums.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 校验/展开召回的数据集范围（决策②）。
 *
 * <p>非空 datasetIds：要求全部归属当前用户，否则按越权拒绝（建流前 403）。
 * 空 datasetIds：展开为当前用户名下全部数据集（“全库”限定在本人范围，无越权）；若名下无库则返回空标记。</p>
 *
 * <p>Dataset 的 {@code is_deleted} 为 {@code @TableLogic}，MyBatis-Plus 查询自动过滤软删——
 * 传入已软删的 id 会因 selectCount 不足而判为越权，展开也只取未删库。</p>
 */
@Component
@RequiredArgsConstructor
public class RecallScopeResolver {

    private final DatasetMapper datasetMapper;

    public ResolvedScope resolve(Long userId, List<Long> requested) {
        if (requested != null && !requested.isEmpty()) {
            List<Long> distinct = requested.stream().distinct().toList();
            // 归属校验：当前用户名下、未软删、命中这些 id 的数量必须等于去重后的请求数量。
            long owned = datasetMapper.selectCount(new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getUserId, userId)
                .in(Dataset::getId, distinct));
            if (owned != distinct.size()) {
                throw new BusinessException(ErrorCode.RECALL_SCOPE_FORBIDDEN);
            }
            return ResolvedScope.of(distinct);
        }

        // 空列表 → 展开为本人全部库（只取 id 列）。
        List<Long> all = datasetMapper.selectList(new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getUserId, userId)
                .select(Dataset::getId))
            .stream()
            .map(Dataset::getId)
            .toList();
        return all.isEmpty() ? ResolvedScope.empty() : ResolvedScope.of(all);
    }
}

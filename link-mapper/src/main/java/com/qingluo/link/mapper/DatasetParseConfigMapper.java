package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetParseConfigMapper extends BaseMapper<DatasetParseConfig> {

    default long countModelReferences(Long configId) {
        return selectCount(new LambdaQueryWrapper<DatasetParseConfig>()
            .and(query -> query
                .eq(DatasetParseConfig::getDenseEmbeddingConfigId, configId)
                .or().eq(DatasetParseConfig::getSparseEmbeddingConfigId, configId)
                .or().eq(DatasetParseConfig::getEnhancementChatConfigId, configId)
                .or().eq(DatasetParseConfig::getEnhancementVisionConfigId, configId)
                .or().eq(DatasetParseConfig::getRerankConfigId, configId)));
    }
}

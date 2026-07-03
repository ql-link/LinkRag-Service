package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.ProviderModelSyncJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部模型目录同步任务 Mapper。
 */
@Mapper
public interface ProviderModelSyncJobMapper extends BaseMapper<ProviderModelSyncJob> {
}

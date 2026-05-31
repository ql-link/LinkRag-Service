package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.UsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用量日志 Mapper
 */
@Mapper
public interface UsageLogMapper extends BaseMapper<UsageLog> {
}
package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 LLM 配置 Mapper
 */
@Mapper
public interface UserLLMConfigMapper extends BaseMapper<UserLLMConfig> {
}
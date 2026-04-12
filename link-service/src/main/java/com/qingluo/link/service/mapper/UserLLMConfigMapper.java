package com.qingluo.link.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.entity.UserLLMConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserLLMConfigMapper extends BaseMapper<UserLLMConfig> {

    List<UserLLMConfig> selectByUserId(@Param("userId") String userId);

    UserLLMConfig selectById(@Param("id") String id);

    int insert(UserLLMConfig config);

    int updateById(UserLLMConfig config);

    int deleteById(@Param("id") String id);

    UserLLMConfig selectDefaultByUserId(@Param("userId") String userId);
}
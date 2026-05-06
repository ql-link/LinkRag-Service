package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 LLM 配置 Mapper
 */
@Mapper
public interface UserLLMConfigMapper extends BaseMapper<UserLLMConfig> {

    List<UserLLMConfig> selectByUserConditions(@Param("userId") Long userId,
                                               @Param("providerType") String providerType,
                                               @Param("capability") String capability,
                                               @Param("isActive") Boolean isActive);

    UserLLMConfig selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    UserLLMConfig selectDefaultByUserIdAndCapability(@Param("userId") Long userId,
                                                     @Param("capability") String capability);

    List<UserLLMConfig> selectDefaultsByUserId(@Param("userId") Long userId);

    long countByUserModelCapability(@Param("userId") Long userId,
                                    @Param("providerId") Long providerId,
                                    @Param("modelName") String modelName,
                                    @Param("capability") String capability);

    int clearDefaultByUserIdAndCapability(@Param("userId") Long userId,
                                          @Param("capability") String capability,
                                          @Param("excludeConfigId") Long excludeConfigId);
}

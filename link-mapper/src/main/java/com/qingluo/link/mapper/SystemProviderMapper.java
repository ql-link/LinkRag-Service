package com.qingluo.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LLM 系统厂商配置 Mapper
 */
@Mapper
public interface SystemProviderMapper extends BaseMapper<SystemProvider> {

    List<SystemProvider> selectActiveProviders();

    SystemProvider selectByProviderType(@Param("providerType") String providerType);

    long countByProviderType(@Param("providerType") String providerType);
}

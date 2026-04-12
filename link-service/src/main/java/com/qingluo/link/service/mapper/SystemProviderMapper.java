package com.qingluo.link.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qingluo.link.model.entity.SystemProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemProviderMapper extends BaseMapper<SystemProvider> {

    List<SystemProvider> selectActiveProviders();

    SystemProvider selectByType(@Param("providerType") String providerType);
}
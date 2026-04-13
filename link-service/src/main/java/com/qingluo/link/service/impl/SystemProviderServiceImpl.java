package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.service.SystemProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统厂商服务实现
 */
@Service
@RequiredArgsConstructor
public class SystemProviderServiceImpl implements SystemProviderService {

    private final SystemProviderMapper systemProviderMapper;

    @Override
    public List<SystemProvider> getActiveProviders() {
        return systemProviderMapper.selectList(
            new LambdaQueryWrapper<SystemProvider>()
                .eq(SystemProvider::getIsActive, true)
                .orderByDesc(SystemProvider::getPriority)
        );
    }

    @Override
    public SystemProvider getByProviderType(String providerType) {
        SystemProvider provider = systemProviderMapper.selectOne(
            new LambdaQueryWrapper<SystemProvider>()
                .eq(SystemProvider::getProviderType, providerType)
        );

        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        return provider;
    }
}
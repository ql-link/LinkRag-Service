package com.qingluo.link.service.impl;

import com.qingluo.link.model.entity.SystemProvider;
import com.qingluo.link.service.SystemProviderService;
import com.qingluo.link.service.mapper.SystemProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统厂商服务实现
 */
@Service
@RequiredArgsConstructor
public class SystemProviderServiceImpl implements SystemProviderService {

    private final SystemProviderMapper providerMapper;

    @Override
    public List<SystemProvider> getActiveProviders() {
        return providerMapper.selectActiveProviders();
    }

    @Override
    public List<SystemProvider> listAllProviders() {
        return providerMapper.selectList(null);
    }
}
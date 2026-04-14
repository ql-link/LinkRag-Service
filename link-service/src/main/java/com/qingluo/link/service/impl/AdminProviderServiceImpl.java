package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.components.redis.service.DoubleDeleteCacheService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.AdminProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminProviderServiceImpl implements AdminProviderService {

    private final SystemProviderMapper systemProviderMapper;
    private final DoubleDeleteCacheService doubleDeleteCacheService;

    @Override
    public PageResult<SystemProvider> listProviders(int page, int size) {
        Page<SystemProvider> pageParam = new Page<>(page, size);
        Page<SystemProvider> result = systemProviderMapper.selectPage(pageParam,
                new LambdaQueryWrapper<SystemProvider>().orderByDesc(SystemProvider::getPriority));

        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public void createProvider(CreateProviderRequest request) {
        long count = systemProviderMapper.selectCount(
                new LambdaQueryWrapper<SystemProvider>()
                        .eq(SystemProvider::getProviderType, request.getProviderType()));

        if (count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_USER_CONFIG, "厂商类型已存在");
        }

        SystemProvider provider = new SystemProvider();
        provider.setProviderType(request.getProviderType());
        provider.setProviderName(request.getProviderName());
        provider.setApiBaseUrl(request.getApiBaseUrl());
        provider.setSupportedModels(request.getSupportedModels());
        provider.setConfigSchema(request.getConfigSchema());
        provider.setIsActive(request.getIsActive());
        provider.setPriority(request.getPriority());

        systemProviderMapper.insert(provider);
        doubleDeleteCacheService.evictProviderCache(request.getProviderType());
    }

    @Override
    public void updateProvider(Long id, UpdateProviderRequest request) {
        SystemProvider provider = systemProviderMapper.selectById(id);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }

        if (StringUtils.hasText(request.getProviderName())) {
            provider.setProviderName(request.getProviderName());
        }
        if (StringUtils.hasText(request.getApiBaseUrl())) {
            provider.setApiBaseUrl(request.getApiBaseUrl());
        }
        if (request.getSupportedModels() != null) {
            provider.setSupportedModels(request.getSupportedModels());
        }
        if (request.getConfigSchema() != null) {
            provider.setConfigSchema(request.getConfigSchema());
        }
        if (request.getIsActive() != null) {
            provider.setIsActive(request.getIsActive());
        }
        if (request.getPriority() != null) {
            provider.setPriority(request.getPriority());
        }

        systemProviderMapper.updateById(provider);
        doubleDeleteCacheService.evictProviderCache(String.valueOf(id));
    }

    @Override
    public void deleteProvider(Long id) {
        SystemProvider provider = systemProviderMapper.selectById(id);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }

        systemProviderMapper.deleteById(id);
        doubleDeleteCacheService.evictProviderCache(String.valueOf(id));
    }

    @Override
    public void toggleActive(Long id, boolean isActive) {
        SystemProvider provider = systemProviderMapper.selectById(id);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }

        provider.setIsActive(isActive);
        systemProviderMapper.updateById(provider);
        doubleDeleteCacheService.evictProviderCache(String.valueOf(id));
    }
}

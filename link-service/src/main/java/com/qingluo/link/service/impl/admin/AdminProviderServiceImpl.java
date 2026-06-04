package com.qingluo.link.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.AdminProviderService;
import com.qingluo.link.service.LLMCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端厂商配置服务实现，负责厂商配置的增删改查与启停控制。
 */
@Service
@RequiredArgsConstructor
public class AdminProviderServiceImpl implements AdminProviderService {

    private final SystemProviderMapper systemProviderMapper;
    private final CacheConsistencyService cacheConsistencyService;
    private final LLMCapabilityService llmCapabilityService;

    @Override
    /**
     * 分页查询厂商配置，并按优先级倒序返回。
     */
    public PageResult<SystemProvider> listProviders(int page, int size) {
        Page<SystemProvider> pageParam = new Page<>(page, size);
        Page<SystemProvider> result = systemProviderMapper.selectPage(pageParam,
                new LambdaQueryWrapper<SystemProvider>().orderByDesc(SystemProvider::getPriority));

        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    /**
     * 创建新的系统厂商配置并清理相关缓存。
     */
    public void createProvider(CreateProviderRequest request) {
        long count = systemProviderMapper.selectCount(
                new LambdaQueryWrapper<SystemProvider>()
                        .eq(SystemProvider::getProviderType, request.getProviderType()));

        if (count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_USER_CONFIG, "厂商类型已存在");
        }
        rejectDeprecatedSupportedModels(request.getSupportedModels());
        llmCapabilityService.parseSupportedCapabilities(request.getSupportedCapabilities());

        SystemProvider provider = new SystemProvider();
        provider.setProviderType(request.getProviderType());
        provider.setProviderName(request.getProviderName());
        provider.setApiBaseUrl(request.getApiBaseUrl());
        provider.setSupportedCapabilities(request.getSupportedCapabilities());
        provider.setConfigSchema(request.getConfigSchema());
        provider.setIsActive(request.getIsActive());
        provider.setPriority(request.getPriority());

        systemProviderMapper.insert(provider);
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, request.getProviderType());
    }

    @Override
    /**
     * 按需更新指定厂商配置。
     */
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
        rejectDeprecatedSupportedModels(request.getSupportedModels());
        if (request.getSupportedCapabilities() != null) {
            llmCapabilityService.parseSupportedCapabilities(request.getSupportedCapabilities());
            provider.setSupportedCapabilities(request.getSupportedCapabilities());
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
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, provider.getProviderType());
    }

    private void rejectDeprecatedSupportedModels(String supportedModels) {
        if (supportedModels != null) {
            throw new BusinessException(ErrorCode.INVALID_PROVIDER_CONFIG, "supportedModels 已废弃，请使用 supportedCapabilities");
        }
    }

    @Override
    /**
     * 删除指定厂商配置并清理缓存。
     */
    public void deleteProvider(Long id) {
        SystemProvider provider = systemProviderMapper.selectById(id);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }

        systemProviderMapper.deleteById(id);
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, provider.getProviderType());
    }

    @Override
    /**
     * 切换厂商启用状态。
     */
    public void toggleActive(Long id, boolean isActive) {
        SystemProvider provider = systemProviderMapper.selectById(id);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }

        provider.setIsActive(isActive);
        systemProviderMapper.updateById(provider);
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, provider.getProviderType());
    }
}

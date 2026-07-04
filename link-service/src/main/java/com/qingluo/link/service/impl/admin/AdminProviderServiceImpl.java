package com.qingluo.link.service.impl.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.log.AuditLog;
import com.qingluo.link.core.util.AuthContext;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.AdminProviderService;
import com.qingluo.link.service.LLMProtocolService;
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
    private final ProviderModelMapper providerModelMapper;
    private final CacheConsistencyService cacheConsistencyService;
    private final LLMProtocolService llmProtocolService;

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
        llmProtocolService.validateProtocol(request.getDefaultProtocol());
        long count = systemProviderMapper.selectCount(
                new LambdaQueryWrapper<SystemProvider>()
                        .eq(SystemProvider::getProviderType, request.getProviderType()));

        if (count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_USER_CONFIG, "厂商类型已存在");
        }
        if (Boolean.TRUE.equals(request.getIsActive())) {
            throw new BusinessException(ErrorCode.PROVIDER_HAS_NO_ACTIVE_MODEL);
        }

        SystemProvider provider = new SystemProvider();
        provider.setProviderType(request.getProviderType());
        provider.setProviderName(request.getProviderName());
        provider.setIconUrl(request.getIconUrl());
        provider.setIconObjectKey(request.getIconObjectKey());
        provider.setApiBaseUrl(request.getApiBaseUrl());
        provider.setDefaultProtocol(request.getDefaultProtocol());
        provider.setIsActive(request.getIsActive());
        provider.setPriority(request.getPriority());

        systemProviderMapper.insert(provider);
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, request.getProviderType());
        AuditLog.event("PROVIDER_CREATE", "operatorId={}, providerId={}, providerType={}",
                AuthContext.getCurrentUserId(), provider.getId(), request.getProviderType());
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
        if (request.getIconUrl() != null) {
            provider.setIconUrl(StringUtils.hasText(request.getIconUrl()) ? request.getIconUrl() : null);
        }
        if (request.getIconObjectKey() != null) {
            provider.setIconObjectKey(StringUtils.hasText(request.getIconObjectKey()) ? request.getIconObjectKey() : null);
        }
        if (StringUtils.hasText(request.getApiBaseUrl())) {
            provider.setApiBaseUrl(request.getApiBaseUrl());
        }
        if (StringUtils.hasText(request.getDefaultProtocol())) {
            llmProtocolService.validateProtocol(request.getDefaultProtocol());
            provider.setDefaultProtocol(request.getDefaultProtocol());
        }
        if (request.getIsActive() != null) {
            if (Boolean.TRUE.equals(request.getIsActive())) {
                requireAtLeastOneActiveModel(provider.getId());
            }
            provider.setIsActive(request.getIsActive());
        }
        if (request.getPriority() != null) {
            provider.setPriority(request.getPriority());
        }

        systemProviderMapper.updateById(provider);
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, provider.getProviderType());
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
        AuditLog.event("PROVIDER_DELETE", "operatorId={}, providerId={}, providerType={}",
                AuthContext.getCurrentUserId(), id, provider.getProviderType());
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
        if (isActive) {
            requireAtLeastOneActiveModel(provider.getId());
        }

        provider.setIsActive(isActive);
        systemProviderMapper.updateById(provider);
        cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, provider.getProviderType());
    }

    private void requireAtLeastOneActiveModel(Long providerId) {
        long activeModelCount = providerModelMapper.selectCount(
                new LambdaQueryWrapper<ProviderModel>()
                        .eq(ProviderModel::getProviderId, providerId)
                        .eq(ProviderModel::getIsActive, true));
        if (activeModelCount <= 0) {
            throw new BusinessException(ErrorCode.PROVIDER_HAS_NO_ACTIVE_MODEL);
        }
    }
}

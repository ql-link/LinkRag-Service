package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 厂商模型能力目录服务实现。
 *
 * <p>目录数据迁出 supported_models JSON 后落到 llm_provider_model 正式表，
 * 本类承接对该表的查询与管理端维护。</p>
 */
@Service
@RequiredArgsConstructor
public class ProviderModelServiceImpl implements ProviderModelService {

    private final ProviderModelMapper providerModelMapper;
    private final SystemProviderMapper systemProviderMapper;
    private final LLMCapabilityService llmCapabilityService;
    private final CacheConsistencyService cacheConsistencyService;

    @Override
    public List<ProviderModel> listActiveModels(Long providerId, String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        LambdaQueryWrapper<ProviderModel> wrapper = new LambdaQueryWrapper<ProviderModel>()
                .eq(ProviderModel::getProviderId, providerId)
                .eq(ProviderModel::getIsActive, true);
        if (normalizedCapability != null) {
            wrapper.eq(ProviderModel::getCapability, normalizedCapability);
        }
        return providerModelMapper.selectList(wrapper);
    }

    @Override
    public List<ProviderModel> listActiveModelsByProviderIds(List<Long> providerIds, String capability) {
        if (providerIds == null || providerIds.isEmpty()) {
            return List.of();
        }
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        // provider_id IN (...) 命中 idx_provider_cap，一次查回全部厂商的上架模型
        LambdaQueryWrapper<ProviderModel> wrapper = new LambdaQueryWrapper<ProviderModel>()
                .in(ProviderModel::getProviderId, providerIds)
                .eq(ProviderModel::getIsActive, true);
        if (normalizedCapability != null) {
            wrapper.eq(ProviderModel::getCapability, normalizedCapability);
        }
        return providerModelMapper.selectList(wrapper);
    }

    @Override
    public boolean isModelCapabilityActive(Long providerId, String modelName, String capability) {
        String normalizedCapability = normalizeCapability(capability);
        return providerModelMapper.selectCount(
                new LambdaQueryWrapper<ProviderModel>()
                        .eq(ProviderModel::getProviderId, providerId)
                        .eq(ProviderModel::getModelName, modelName)
                        .eq(ProviderModel::getCapability, normalizedCapability)
                        .eq(ProviderModel::getIsActive, true)
        ) > 0;
    }

    @Override
    @Transactional
    /**
     * 新增模型能力目录项。已存在同 (厂商,模型,能力) 时幂等确保其上架，
     * 避免管理员重复新增报唯一约束冲突。
     */
    public ProviderModel addModelCapability(Long providerId, String modelName, String capability) {
        SystemProvider provider = requireProvider(providerId);
        String normalizedCapability = normalizeCapability(capability);

        ProviderModel existing = providerModelMapper.selectOne(
                new LambdaQueryWrapper<ProviderModel>()
                        .eq(ProviderModel::getProviderId, providerId)
                        .eq(ProviderModel::getModelName, modelName)
                        .eq(ProviderModel::getCapability, normalizedCapability)
        );
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getIsActive())) {
                existing.setIsActive(true);
                providerModelMapper.updateById(existing);
            }
            evictProviderCache(provider);
            return existing;
        }

        ProviderModel model = new ProviderModel();
        model.setProviderId(providerId);
        model.setModelName(modelName);
        model.setCapability(normalizedCapability);
        model.setIsActive(true);
        providerModelMapper.insert(model);
        evictProviderCache(provider);
        return model;
    }

    @Override
    @Transactional
    public void deleteModelCapability(Long id) {
        ProviderModel model = requireModel(id);
        providerModelMapper.deleteById(id);
        evictProviderCache(systemProviderMapper.selectById(model.getProviderId()));
    }

    @Override
    @Transactional
    public void toggleModelCapability(Long id, boolean isActive) {
        ProviderModel model = requireModel(id);
        model.setIsActive(isActive);
        providerModelMapper.updateById(model);
        evictProviderCache(systemProviderMapper.selectById(model.getProviderId()));
    }

    private SystemProvider requireProvider(Long providerId) {
        SystemProvider provider = systemProviderMapper.selectById(providerId);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        return provider;
    }

    private ProviderModel requireModel(Long id) {
        ProviderModel model = providerModelMapper.selectById(id);
        if (model == null) {
            throw new NotFoundException(ErrorCode.MODEL_NOT_SUPPORTED, "模型目录项不存在");
        }
        return model;
    }

    private void evictProviderCache(SystemProvider provider) {
        if (provider != null) {
            cacheConsistencyService.evict(CacheEvictTarget.SYSTEM_PROVIDER, provider.getProviderType());
        }
    }

    /**
     * 能力必填校验并归一化为大写。
     */
    private String normalizeCapability(String capability) {
        llmCapabilityService.validateCapability(capability);
        return capability.toUpperCase(Locale.ROOT);
    }

    /**
     * 能力可空校验，为空返回 null 表示不过滤。
     */
    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        return normalizeCapability(capability);
    }
}

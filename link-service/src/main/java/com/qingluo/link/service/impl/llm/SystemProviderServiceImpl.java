package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.FetchProviderModelsRequest;
import com.qingluo.link.model.dto.response.ProviderDTO;
import com.qingluo.link.model.dto.response.ProviderModelListDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.SystemProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 系统厂商服务实现
 */
@Service
@RequiredArgsConstructor
public class SystemProviderServiceImpl implements SystemProviderService {

    private final SystemProviderMapper systemProviderMapper;
    private final LLMCapabilityService llmCapabilityService;
    private final ProviderModelFetchService providerModelFetchService;

    @Override
    /**
     * 查询所有启用中的系统厂商配置。
     */
    public List<SystemProvider> getActiveProviders() {
        return systemProviderMapper.selectList(
            new LambdaQueryWrapper<SystemProvider>()
                .eq(SystemProvider::getIsActive, true)
                .orderByDesc(SystemProvider::getPriority)
        );
    }

    @Override
    /**
     * 查询用户侧可用厂商，按能力过滤。
     */
    public List<ProviderDTO> getActiveProvidersByCapability(String capability) {
        String normalizedCapability = normalizeRequiredCapability(capability);
        return getActiveProviders().stream()
                .filter(provider -> llmCapabilityService
                        .parseSupportedCapabilities(provider.getSupportedCapabilities())
                        .contains(normalizedCapability))
                .map(provider -> toProviderDTO(provider))
                .toList();
    }

    @Override
    public ProviderModelListDTO fetchProviderModels(Long providerId, FetchProviderModelsRequest request) {
        return providerModelFetchService.fetchModels(getActiveByProviderId(providerId), request);
    }

    @Override
    /**
     * 按厂商类型查询系统厂商配置。
     */
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

    @Override
    /**
     * 按厂商类型查询启用中的系统厂商配置，禁用时抛出异常。
     */
    public SystemProvider getActiveByProviderType(String providerType) {
        SystemProvider provider = getByProviderType(providerType);
        if (!Boolean.TRUE.equals(provider.getIsActive())) {
            throw new BusinessException(ErrorCode.PROVIDER_DISABLED);
        }
        return provider;
    }

    @Override
    public SystemProvider getActiveByProviderId(Long providerId) {
        SystemProvider provider = systemProviderMapper.selectById(providerId);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        if (!Boolean.TRUE.equals(provider.getIsActive())) {
            throw new BusinessException(ErrorCode.PROVIDER_DISABLED);
        }
        return provider;
    }

    /**
     * 将厂商实体转换为用户侧厂商 DTO。
     */
    private ProviderDTO toProviderDTO(SystemProvider provider) {
        return new ProviderDTO(
                provider.getId(),
                provider.getProviderType(),
                provider.getProviderName(),
                provider.getApiBaseUrl(),
                llmCapabilityService.parseSupportedCapabilities(provider.getSupportedCapabilities()));
    }

    /**
     * 归一化并校验能力值。
     */
    private String normalizeRequiredCapability(String capability) {
        if (!StringUtils.hasText(capability)) {
            throw new BusinessException(ErrorCode.INVALID_MODEL_CAPABILITY, "能力不能为空");
        }
        String normalized = capability.toUpperCase(Locale.ROOT);
        llmCapabilityService.validateCapability(normalized);
        return normalized;
    }
}

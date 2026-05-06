package com.qingluo.link.service.impl.llm;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.response.ModelCapabilityDTO;
import com.qingluo.link.model.dto.response.ProviderModelDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.SystemProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 系统厂商服务实现
 */
@Service
@RequiredArgsConstructor
public class SystemProviderServiceImpl implements SystemProviderService {

    private final SystemProviderMapper systemProviderMapper;
    private final LLMCapabilityService llmCapabilityService;

    @Override
    public List<SystemProvider> getActiveProviders() {
        return systemProviderMapper.selectActiveProviders();
    }

    @Override
    public List<ProviderModelDTO> getActiveProviderModels(String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        return systemProviderMapper.selectActiveProviders().stream()
                .map(provider -> toProviderModelDTO(provider, normalizedCapability))
                .filter(dto -> !dto.getModels().isEmpty())
                .toList();
    }

    @Override
    public SystemProvider getByProviderType(String providerType) {
        SystemProvider provider = systemProviderMapper.selectByProviderType(providerType);
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        return provider;
    }

    @Override
    public SystemProvider getActiveByProviderType(String providerType) {
        SystemProvider provider = getByProviderType(providerType);
        if (!Boolean.TRUE.equals(provider.getIsActive())) {
            throw new BusinessException(ErrorCode.PROVIDER_DISABLED);
        }
        return provider;
    }

    private ProviderModelDTO toProviderModelDTO(SystemProvider provider, String capability) {
        Map<String, List<String>> supportedModels =
                llmCapabilityService.parseSupportedModels(provider.getSupportedModels());
        List<ModelCapabilityDTO> models = supportedModels.entrySet().stream()
                .filter(entry -> capability == null || entry.getValue().contains(capability))
                .map(entry -> new ModelCapabilityDTO(entry.getKey(), entry.getValue()))
                .toList();
        return new ProviderModelDTO(provider.getProviderType(), provider.getProviderName(), models);
    }

    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        String normalized = capability.toUpperCase();
        llmCapabilityService.validateCapability(normalized);
        return normalized;
    }
}

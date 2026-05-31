package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Locale;
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
     * 查询用户侧可用厂商与模型，可按能力过滤；过滤后无可选模型的厂商不返回。
     */
    public List<ProviderModelDTO> getActiveProviderModels(String capability) {
        String normalizedCapability = normalizeCapabilityIfPresent(capability);
        return getActiveProviders().stream()
                .map(provider -> toProviderModelDTO(provider, normalizedCapability))
                .filter(dto -> !dto.getModels().isEmpty())
                .toList();
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

    /**
     * 将厂商实体转换为用户侧厂商模型 DTO，并按能力过滤模型。
     */
    private ProviderModelDTO toProviderModelDTO(SystemProvider provider, String capability) {
        Map<String, List<String>> supportedModels =
                llmCapabilityService.parseSupportedModels(provider.getSupportedModels());
        List<ModelCapabilityDTO> models = supportedModels.entrySet().stream()
                .filter(entry -> capability == null || entry.getValue().contains(capability))
                .map(entry -> new ModelCapabilityDTO(entry.getKey(), entry.getValue()))
                .toList();
        return new ProviderModelDTO(provider.getProviderType(), provider.getProviderName(), models);
    }

    /**
     * 归一化并校验能力值，为空时返回 null 表示不过滤。
     */
    private String normalizeCapabilityIfPresent(String capability) {
        if (!StringUtils.hasText(capability)) {
            return null;
        }
        String normalized = capability.toUpperCase(Locale.ROOT);
        llmCapabilityService.validateCapability(normalized);
        return normalized;
    }
}

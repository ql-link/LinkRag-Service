package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreatePresetRequest;
import com.qingluo.link.model.dto.request.UpdatePresetRequest;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMProtocolService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.SystemPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 系统预设服务实现。
 *
 * <p>预设以 LinkRag 平台 Key 形式集中维护；用户没有自配生效模型时由
 * {@link EffectiveLLMConfigServiceImpl} 回退读取。本服务在事务内保证同一能力同时只有一条
 * active + default 的系统兜底预设。</p>
 */
@Service
@RequiredArgsConstructor
public class SystemPresetServiceImpl implements SystemPresetService {

    private static final String LINKRAG_PROVIDER_TYPE = "linkrag";

    private final SystemPresetMapper systemPresetMapper;
    private final SystemProviderMapper systemProviderMapper;
    private final ProviderModelMapper providerModelMapper;
    private final ProviderModelService providerModelService;
    private final LLMCapabilityService llmCapabilityService;
    private final LLMProtocolService llmProtocolService;
    private final ApiKeyEncryptService apiKeyEncryptService;

    @Override
    @Transactional
    public SystemPreset createPreset(CreatePresetRequest request) {
        SystemProvider linkRagProvider = requireLinkRagProvider();
        PresetFacts facts = resolveCreateFacts(request);

        SystemPreset preset = new SystemPreset();
        preset.setProviderId(linkRagProvider.getId());
        preset.setModelName(facts.modelName());
        preset.setDisplayName(facts.displayName());
        preset.setCapability(facts.capability());
        preset.setProviderType(LINKRAG_PROVIDER_TYPE);
        preset.setProtocol(facts.protocol());
        preset.setApiBaseUrl(facts.apiBaseUrl());
        preset.setApiKey(apiKeyEncryptService.encrypt(request.getApiKey()));
        preset.setIsActive(true);
        preset.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        if (Boolean.TRUE.equals(preset.getIsDefault())) {
            clearDefaultForCapability(facts.capability(), null);
        }
        systemPresetMapper.insert(preset);
        return preset;
    }

    @Override
    @Transactional
    public void deletePreset(Long id) {
        SystemPreset preset = systemPresetMapper.selectById(id);
        if (preset == null) {
            throw new NotFoundException(ErrorCode.USER_CONFIG_NOT_FOUND, "系统预设不存在");
        }
        systemPresetMapper.deleteById(id);
    }

    @Override
    @Transactional
    public SystemPreset updatePreset(Long id, UpdatePresetRequest request) {
        SystemPreset preset = requirePreset(id);
        SystemProvider linkRagProvider = requireLinkRagProvider();
        applyUpdateFactsIfPresent(preset, request, linkRagProvider.getId());
        if (StringUtils.hasText(request.getApiKey())) {
            preset.setApiKey(apiKeyEncryptService.encrypt(request.getApiKey()));
        }
        if (request.getIsActive() != null) {
            if (Boolean.FALSE.equals(request.getIsActive()) && Boolean.TRUE.equals(preset.getIsDefault())) {
                throw new BusinessException(400, "当前系统默认预设不能直接禁用，请先指定替代默认预设", 400);
            }
            preset.setIsActive(request.getIsActive());
        }
        if (request.getIsDefault() != null) {
            if (Boolean.TRUE.equals(request.getIsDefault())) {
                if (!Boolean.TRUE.equals(preset.getIsActive())) {
                    throw new BusinessException(400, "禁用的系统预设不能设为默认", 400);
                }
                clearDefaultForCapability(preset.getCapability(), preset.getId());
            }
            preset.setIsDefault(request.getIsDefault());
        }

        systemPresetMapper.updateById(preset);
        return preset;
    }

    @Override
    @Transactional
    public void togglePreset(Long id, boolean isActive) {
        SystemPreset preset = requirePreset(id);
        if (!isActive && Boolean.TRUE.equals(preset.getIsDefault())) {
            throw new BusinessException(400, "当前系统默认预设不能直接禁用，请先指定替代默认预设", 400);
        }
        preset.setIsActive(isActive);
        systemPresetMapper.updateById(preset);
    }

    @Override
    @Transactional
    public void setDefaultPreset(Long id) {
        SystemPreset preset = requirePreset(id);
        if (!Boolean.TRUE.equals(preset.getIsActive())) {
            throw new BusinessException(400, "禁用的系统预设不能设为默认", 400);
        }
        clearDefaultForCapability(preset.getCapability(), preset.getId());
        preset.setIsDefault(true);
        systemPresetMapper.updateById(preset);
    }

    /**
     * 列出 LinkRag 系统预设，Key 脱敏返回（不向管理端暴露平台 Key 明文）。
     */
    @Override
    public List<SystemPreset> listPresets() {
        List<SystemPreset> presets = systemPresetMapper.selectList(new LambdaQueryWrapper<SystemPreset>()
                .eq(SystemPreset::getProviderType, LINKRAG_PROVIDER_TYPE)
                .orderByAsc(SystemPreset::getCapability)
                .orderByDesc(SystemPreset::getIsDefault)
                .orderByAsc(SystemPreset::getModelName));
        presets.forEach(preset -> preset.setApiKey(apiKeyEncryptService.maskApiKey(preset.getApiKey())));
        return presets;
    }

    private SystemPreset requirePreset(Long id) {
        SystemPreset preset = systemPresetMapper.selectById(id);
        if (preset == null) {
            throw new NotFoundException(ErrorCode.USER_CONFIG_NOT_FOUND, "系统预设不存在");
        }
        return preset;
    }

    private void applyCatalogFacts(SystemPreset preset, Long linkRagProviderId, Long sourceProviderId,
                                   String modelName, String capability) {
        String normalizedCapability = normalizeCapability(capability);
        ProviderModel model = providerModelService.findActiveModelCapability(sourceProviderId, modelName, normalizedCapability);
        if (model == null) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "目录中无该模型能力，无法预设");
        }
        applyFacts(preset, linkRagProviderId, factsFromModel(model));
    }

    private void applySourceModelFacts(SystemPreset preset, Long linkRagProviderId, Long sourceProviderModelId) {
        ProviderModel model = providerModelMapper.selectById(sourceProviderModelId);
        if (model == null) {
            throw new NotFoundException(ErrorCode.MODEL_NOT_SUPPORTED, "模型目录项不存在");
        }
        if (!Boolean.TRUE.equals(model.getIsActive())) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "模型目录项未上架，无法加入 LinkRag 兜底");
        }
        applyFacts(preset, linkRagProviderId, factsFromModel(model));
    }

    private void applyManualFacts(SystemPreset preset, Long linkRagProviderId, String modelName, String displayName,
                                  String capability, String protocol, String apiBaseUrl) {
        PresetFacts facts = normalizeManualFacts(modelName, displayName, capability, protocol, apiBaseUrl);
        applyFacts(preset, linkRagProviderId, facts);
    }

    private void applyFacts(SystemPreset preset, Long linkRagProviderId, PresetFacts facts) {
        preset.setProviderId(linkRagProviderId);
        preset.setModelName(facts.modelName());
        preset.setDisplayName(facts.displayName());
        preset.setCapability(facts.capability());
        preset.setProviderType(LINKRAG_PROVIDER_TYPE);
        preset.setProtocol(facts.protocol());
        preset.setApiBaseUrl(facts.apiBaseUrl());
    }

    private void clearDefaultForCapability(String capability, Long excludeId) {
        LambdaUpdateWrapper<SystemPreset> wrapper = new LambdaUpdateWrapper<SystemPreset>()
                .eq(SystemPreset::getProviderType, LINKRAG_PROVIDER_TYPE)
                .eq(SystemPreset::getCapability, capability)
                .eq(SystemPreset::getIsDefault, true)
                .set(SystemPreset::getIsDefault, false);
        if (excludeId != null) {
            wrapper.ne(SystemPreset::getId, excludeId);
        }
        systemPresetMapper.update(null, wrapper);
    }

    private PresetFacts resolveCreateFacts(CreatePresetRequest request) {
        if (request.getSourceProviderModelId() != null) {
            ProviderModel model = providerModelMapper.selectById(request.getSourceProviderModelId());
            if (model == null) {
                throw new NotFoundException(ErrorCode.MODEL_NOT_SUPPORTED, "模型目录项不存在");
            }
            if (!Boolean.TRUE.equals(model.getIsActive())) {
                throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "模型目录项未上架，无法加入 LinkRag 兜底");
            }
            return factsFromModel(model);
        }
        if (request.getProviderId() != null) {
            String capability = requireCapability(request.getCapability());
            ProviderModel model = providerModelService.findActiveModelCapability(
                    request.getProviderId(), requireText(request.getModelName(), "模型名称不能为空"), capability);
            if (model == null) {
                throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "目录中无该模型能力，无法预设");
            }
            return factsFromModel(model);
        }
        return normalizeManualFacts(
                request.getModelName(),
                request.getDisplayName(),
                request.getCapability(),
                request.getProtocol(),
                request.getApiBaseUrl()
        );
    }

    private void applyUpdateFactsIfPresent(SystemPreset preset, UpdatePresetRequest request, Long linkRagProviderId) {
        if (request.getSourceProviderModelId() != null) {
            applySourceModelFacts(preset, linkRagProviderId, request.getSourceProviderModelId());
            return;
        }
        if (request.getProviderId() != null) {
            String modelName = StringUtils.hasText(request.getModelName()) ? request.getModelName() : preset.getModelName();
            String capability = StringUtils.hasText(request.getCapability()) ? request.getCapability() : preset.getCapability();
            applyCatalogFacts(preset, linkRagProviderId, request.getProviderId(), modelName, capability);
            return;
        }
        if (hasManualFactUpdate(request)) {
            String modelName = StringUtils.hasText(request.getModelName()) ? request.getModelName() : preset.getModelName();
            String displayName = request.getDisplayName() != null ? request.getDisplayName() : preset.getDisplayName();
            String capability = StringUtils.hasText(request.getCapability()) ? request.getCapability() : preset.getCapability();
            String protocol = StringUtils.hasText(request.getProtocol()) ? request.getProtocol() : preset.getProtocol();
            String apiBaseUrl = StringUtils.hasText(request.getApiBaseUrl()) ? request.getApiBaseUrl() : preset.getApiBaseUrl();
            applyManualFacts(preset, linkRagProviderId, modelName, displayName, capability, protocol, apiBaseUrl);
        }
    }

    private boolean hasManualFactUpdate(UpdatePresetRequest request) {
        return StringUtils.hasText(request.getModelName())
                || request.getDisplayName() != null
                || StringUtils.hasText(request.getCapability())
                || StringUtils.hasText(request.getProtocol())
                || StringUtils.hasText(request.getApiBaseUrl());
    }

    private SystemProvider requireLinkRagProvider() {
        SystemProvider provider = systemProviderMapper.selectOne(new LambdaQueryWrapper<SystemProvider>()
                .eq(SystemProvider::getProviderType, LINKRAG_PROVIDER_TYPE));
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        return provider;
    }

    private PresetFacts factsFromModel(ProviderModel model) {
        if (!StringUtils.hasText(model.getProtocol()) || !StringUtils.hasText(model.getApiBaseUrl())) {
            throw new BusinessException(ErrorCode.MODEL_CONFIG_INCOMPLETE);
        }
        return new PresetFacts(
                requireText(model.getModelName(), "模型名称不能为空"),
                normalizeDisplayName(model.getDisplayName()),
                normalizeCapability(model.getCapability()),
                model.getProtocol(),
                model.getApiBaseUrl()
        );
    }

    private PresetFacts normalizeManualFacts(String modelName, String displayName, String capability,
                                             String protocol, String apiBaseUrl) {
        String normalizedProtocol = requireText(protocol, "调用协议不能为空");
        llmProtocolService.validateProtocol(normalizedProtocol);
        String normalizedApiBaseUrl = requireText(apiBaseUrl, "调用入口不能为空");
        return new PresetFacts(
                requireText(modelName, "模型名称不能为空"),
                normalizeDisplayName(displayName),
                requireCapability(capability),
                normalizedProtocol,
                normalizedApiBaseUrl
        );
    }

    private String normalizeCapability(String capability) {
        llmCapabilityService.validateCapability(capability);
        return capability.toUpperCase(Locale.ROOT);
    }

    private String requireCapability(String capability) {
        return normalizeCapability(requireText(capability, "能力标识不能为空"));
    }

    private String normalizeDisplayName(String displayName) {
        return StringUtils.hasText(displayName) ? displayName.trim() : null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, message, 400);
        }
        return value.trim();
    }

    private record PresetFacts(String modelName, String displayName, String capability,
                               String protocol, String apiBaseUrl) {
    }
}

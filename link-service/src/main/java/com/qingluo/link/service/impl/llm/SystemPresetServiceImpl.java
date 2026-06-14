package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.model.dto.request.CreatePresetRequest;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.SystemPresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 系统预设服务实现。
 *
 * <p>预设以平台 Key 形式集中维护；注册时复制进用户配置表。预设与用户配置字段对齐，
 * provider_type/protocol/api_base_url 在创建预设时从模型能力层复制并自带，镜像时直接平移，
 * 不再 join 厂商表；api_key 密文原样搬运（仅在真正调用 LLM 时才解密）。</p>
 */
@Service
@RequiredArgsConstructor
public class SystemPresetServiceImpl implements SystemPresetService {

    private final SystemPresetMapper systemPresetMapper;
    private final SystemProviderMapper systemProviderMapper;
    private final UserLLMConfigMapper userLLMConfigMapper;
    private final ProviderModelService providerModelService;
    private final LLMCapabilityService llmCapabilityService;
    private final ApiKeyEncryptService apiKeyEncryptService;

    @Override
    @Transactional
    /**
     * 注册时复制全部 active 预设进用户配置表。
     *
     * <p>同一能力只让首条预设 is_default=true（生效唯一），其余作为常备备选；
     * 已存在同预设行则跳过，保证注册写入（含重试触发多次）幂等不重复。</p>
     */
    public void applyPresetsForNewUser(Long userId) {
        List<SystemPreset> presets = systemPresetMapper.selectList(
                new LambdaQueryWrapper<SystemPreset>().eq(SystemPreset::getIsActive, true)
        );

        Set<String> defaultedCapabilities = new HashSet<>();
        for (SystemPreset preset : presets) {
            SystemProvider provider = systemProviderMapper.selectById(preset.getProviderId());
            if (provider == null) {
                // 预设关联的厂商已被删除，跳过该条，不阻断整体注册
                continue;
            }
            if (presetAlreadyApplied(userId, preset)) {
                continue;
            }

            // 同一能力仅首条预设设为生效，避免单能力出现多个生效
            boolean asDefault = defaultedCapabilities.add(preset.getCapability());

            // 预设自带 provider_type/protocol/api_base_url 事实，直接平移，不再 join 厂商表取值
            UserLLMConfig config = new UserLLMConfig();
            config.setUserId(userId);
            config.setProviderId(preset.getProviderId());
            config.setProviderType(preset.getProviderType());
            config.setApiKey(preset.getApiKey());
            config.setApiBaseUrl(preset.getApiBaseUrl());
            config.setProtocol(preset.getProtocol());
            config.setModelName(preset.getModelName());
            config.setCapability(preset.getCapability());
            config.setIsActive(true);
            config.setIsDefault(asDefault);
            config.setIsSystemPreset(true);
            userLLMConfigMapper.insert(config);
        }
    }

    @Override
    @Transactional
    public SystemPreset createPreset(CreatePresetRequest request) {
        SystemProvider provider = systemProviderMapper.selectById(request.getProviderId());
        if (provider == null) {
            throw NotFoundException.providerNotFound();
        }
        llmCapabilityService.validateCapability(request.getCapability());
        String capability = request.getCapability().toUpperCase(Locale.ROOT);
        // 预设事实来源唯一指向模型能力层：取该 (模型,能力) 上架行，复制其协议与入口
        ProviderModel model = providerModelService.findActiveModelCapability(
                request.getProviderId(), request.getModelName(), capability);
        if (model == null) {
            throw new BusinessException(ErrorCode.MODEL_NOT_SUPPORTED, "目录中无该模型能力，无法预设");
        }
        if (!StringUtils.hasText(model.getProtocol()) || !StringUtils.hasText(model.getApiBaseUrl())) {
            throw new BusinessException(ErrorCode.MODEL_CONFIG_INCOMPLETE);
        }

        SystemPreset preset = new SystemPreset();
        preset.setProviderId(request.getProviderId());
        preset.setModelName(request.getModelName());
        preset.setCapability(capability);
        preset.setProviderType(provider.getProviderType());
        preset.setProtocol(model.getProtocol());
        preset.setApiBaseUrl(model.getApiBaseUrl());
        preset.setApiKey(apiKeyEncryptService.encrypt(request.getApiKey()));
        preset.setIsActive(true);
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
    /**
     * 列出全部系统预设，Key 脱敏返回（不向管理端暴露平台 Key 明文）。
     */
    public List<SystemPreset> listPresets() {
        List<SystemPreset> presets = systemPresetMapper.selectList(null);
        presets.forEach(preset -> preset.setApiKey(apiKeyEncryptService.maskApiKey(preset.getApiKey())));
        return presets;
    }

    /**
     * 判断该用户是否已存在同 (厂商,模型,能力) 的预设行，用于注册写入幂等。
     */
    private boolean presetAlreadyApplied(Long userId, SystemPreset preset) {
        return userLLMConfigMapper.selectCount(
                new LambdaQueryWrapper<UserLLMConfig>()
                        .eq(UserLLMConfig::getUserId, userId)
                        .eq(UserLLMConfig::getProviderId, preset.getProviderId())
                        .eq(UserLLMConfig::getModelName, preset.getModelName())
                        .eq(UserLLMConfig::getCapability, preset.getCapability())
                        .eq(UserLLMConfig::getIsSystemPreset, true)
        ) > 0;
    }
}

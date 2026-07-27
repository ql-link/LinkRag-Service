package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.LLMCapabilityDefaultMapper;
import com.qingluo.link.model.dto.entity.LLMCapabilityDefault;
import com.qingluo.link.model.dto.entity.LLMModelConfig;
import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.LLMConfigScope;
import com.qingluo.link.service.LLMCapabilityDefaultService;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMModelConfigValidator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 独立能力默认关系；只负责在未显式指定配置时选择 configId。
 */
@Service
@RequiredArgsConstructor
public class LLMCapabilityDefaultServiceImpl implements LLMCapabilityDefaultService {

    private static final long SYSTEM_OWNER_ID = 0L;
    private static final List<String> CAPABILITIES = List.of(
        "CHAT", "EMBEDDING", "SPARSE_EMBEDDING", "VISION", "RERANK", "ASR");

    private final LLMCapabilityDefaultMapper defaultMapper;
    private final LLMModelConfigValidator configValidator;
    private final LLMCapabilityService capabilityService;

    @Override
    public CapabilityDefaultDTO getEffectiveDefault(Long userId, String capability) {
        return resolve(userId, normalizeCapability(capability), true);
    }

    @Override
    public List<CapabilityDefaultDTO> listDefaults(Long userId) {
        return CAPABILITIES.stream().map(capability -> resolve(userId, capability, false)).toList();
    }

    @Override
    @Transactional
    public CapabilityDefaultDTO setUserDefault(Long userId, String capability, Long configId) {
        String normalized = normalizeCapability(capability);
        LLMModelConfig config = configValidator.requireExecutable(userId, configId, normalized);
        if (!LLMConfigScope.USER.name().equals(config.getScope())
            || !userId.equals(config.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
        }
        upsert(LLMConfigScope.USER.name(), userId, normalized, configId);
        return resolve(userId, normalized, true);
    }

    @Override
    @Transactional
    public CapabilityDefaultDTO clearUserDefault(Long userId, String capability) {
        String normalized = normalizeCapability(capability);
        defaultMapper.delete(new LambdaQueryWrapper<LLMCapabilityDefault>()
            .eq(LLMCapabilityDefault::getScope, LLMConfigScope.USER.name())
            .eq(LLMCapabilityDefault::getOwnerUserId, userId)
            .eq(LLMCapabilityDefault::getCapability, normalized));
        return resolve(userId, normalized, false);
    }

    @Override
    @Transactional
    public CapabilityDefaultDTO setSystemDefault(String capability, Long configId) {
        String normalized = normalizeCapability(capability);
        LLMModelConfig config = configValidator.requireExecutable(SYSTEM_OWNER_ID, configId, normalized);
        if (!LLMConfigScope.SYSTEM.name().equals(config.getScope())
            || !Long.valueOf(SYSTEM_OWNER_ID).equals(config.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
        }
        try {
            upsert(LLMConfigScope.SYSTEM.name(), SYSTEM_OWNER_ID, normalized, configId);
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.LLM_DEFAULT_UPDATE_FAILED);
        }
        return resolve(SYSTEM_OWNER_ID, normalized, true);
    }

    @Override
    @Transactional
    public CapabilityDefaultDTO clearSystemDefaultForConfig(String capability, Long configId) {
        String normalized = normalizeCapability(capability);
        try {
            defaultMapper.delete(new LambdaQueryWrapper<LLMCapabilityDefault>()
                .eq(LLMCapabilityDefault::getScope, LLMConfigScope.SYSTEM.name())
                .eq(LLMCapabilityDefault::getOwnerUserId, SYSTEM_OWNER_ID)
                .eq(LLMCapabilityDefault::getCapability, normalized)
                .eq(LLMCapabilityDefault::getConfigId, configId));
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.LLM_DEFAULT_UPDATE_FAILED);
        }
        return resolve(SYSTEM_OWNER_ID, normalized, false);
    }

    @Override
    public boolean isSystemDefault(Long configId) {
        if (configId == null) {
            return false;
        }
        return defaultMapper.selectCount(new LambdaQueryWrapper<LLMCapabilityDefault>()
            .eq(LLMCapabilityDefault::getScope, LLMConfigScope.SYSTEM.name())
            .eq(LLMCapabilityDefault::getOwnerUserId, SYSTEM_OWNER_ID)
            .eq(LLMCapabilityDefault::getConfigId, configId)) > 0;
    }

    @Override
    @Transactional
    public void clearUserDefaultForConfig(Long userId, Long configId) {
        defaultMapper.delete(new LambdaQueryWrapper<LLMCapabilityDefault>()
            .eq(LLMCapabilityDefault::getScope, LLMConfigScope.USER.name())
            .eq(LLMCapabilityDefault::getOwnerUserId, userId)
            .eq(LLMCapabilityDefault::getConfigId, configId));
    }

    private CapabilityDefaultDTO resolve(Long userId, String capability, boolean required) {
        LLMCapabilityDefault userDefault = userId == null || userId == SYSTEM_OWNER_ID
            ? null : find(LLMConfigScope.USER.name(), userId, capability);
        LLMCapabilityDefault systemDefault = find(
            LLMConfigScope.SYSTEM.name(), SYSTEM_OWNER_ID, capability);

        Long userDefaultConfigId = userDefault == null ? null : userDefault.getConfigId();
        Long systemConfigId = systemDefault == null ? null : systemDefault.getConfigId();
        Long effectiveConfigId = userDefaultConfigId != null ? userDefaultConfigId : systemConfigId;
        if (effectiveConfigId == null) {
            if (required) {
                throw new BusinessException(ErrorCode.LLM_DEFAULT_NOT_CONFIGURED);
            }
            return new CapabilityDefaultDTO(capability, null, null, null);
        }

        if (userDefaultConfigId != null) {
            LLMModelConfig config = configValidator.requireExecutable(userId, userDefaultConfigId, capability);
            if (!LLMConfigScope.USER.name().equals(config.getScope())
                || !userId.equals(config.getOwnerUserId())) {
                throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
            }
        }
        if (systemConfigId != null) {
            LLMModelConfig config = configValidator.requireExecutable(SYSTEM_OWNER_ID, systemConfigId, capability);
            if (!LLMConfigScope.SYSTEM.name().equals(config.getScope())
                || !Long.valueOf(SYSTEM_OWNER_ID).equals(config.getOwnerUserId())) {
                throw new BusinessException(ErrorCode.LLM_CONFIG_FORBIDDEN);
            }
        }
        return new CapabilityDefaultDTO(capability, userDefaultConfigId, systemConfigId, effectiveConfigId);
    }

    private LLMCapabilityDefault find(String scope, Long ownerUserId, String capability) {
        return defaultMapper.selectOne(new LambdaQueryWrapper<LLMCapabilityDefault>()
            .eq(LLMCapabilityDefault::getScope, scope)
            .eq(LLMCapabilityDefault::getOwnerUserId, ownerUserId)
            .eq(LLMCapabilityDefault::getCapability, capability)
            .last("LIMIT 1"));
    }

    private void upsert(String scope, Long ownerUserId, String capability, Long configId) {
        LLMCapabilityDefault existing = find(scope, ownerUserId, capability);
        if (existing == null) {
            LLMCapabilityDefault created = new LLMCapabilityDefault();
            created.setScope(scope);
            created.setOwnerUserId(ownerUserId);
            created.setCapability(capability);
            created.setConfigId(configId);
            defaultMapper.insert(created);
            return;
        }
        existing.setConfigId(configId);
        defaultMapper.updateById(existing);
    }

    private String normalizeCapability(String capability) {
        capabilityService.validateCapability(capability);
        return capability.toUpperCase(Locale.ROOT);
    }
}

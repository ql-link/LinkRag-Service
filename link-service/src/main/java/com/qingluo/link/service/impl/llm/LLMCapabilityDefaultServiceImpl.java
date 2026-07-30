package com.qingluo.link.service.impl.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.mapper.LLMCapabilityDefaultMapper;
import com.qingluo.link.model.dto.entity.LLMCapabilityDefault;
import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import com.qingluo.link.model.enums.LLMConfigScope;
import com.qingluo.link.service.LLMCapabilityDefaultService;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMModelConfigValidator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户能力默认关系；平台配置与个人配置共用全局 configId。
 */
@Service
@RequiredArgsConstructor
public class LLMCapabilityDefaultServiceImpl implements LLMCapabilityDefaultService {

    private static final List<String> CAPABILITIES = List.of(
        "CHAT", "EMBEDDING", "SPARSE_EMBEDDING", "VISION", "RERANK", "ASR");

    private final LLMCapabilityDefaultMapper defaultMapper;
    private final LLMModelConfigValidator configValidator;
    private final LLMCapabilityService capabilityService;

    @Override
    public CapabilityDefaultDTO getDefault(Long userId, String capability) {
        return resolve(userId, normalizeCapability(capability));
    }

    @Override
    public List<CapabilityDefaultDTO> listDefaults(Long userId) {
        return CAPABILITIES.stream().map(capability -> resolve(userId, capability)).toList();
    }

    @Override
    @Transactional
    public CapabilityDefaultDTO setUserDefault(Long userId, String capability, Long configId) {
        String normalized = normalizeCapability(capability);
        configValidator.requireExecutable(userId, configId, normalized);
        upsert(LLMConfigScope.USER.name(), userId, normalized, configId);
        return resolve(userId, normalized);
    }

    @Override
    @Transactional
    public CapabilityDefaultDTO clearUserDefault(Long userId, String capability) {
        String normalized = normalizeCapability(capability);
        defaultMapper.delete(new LambdaQueryWrapper<LLMCapabilityDefault>()
            .eq(LLMCapabilityDefault::getScope, LLMConfigScope.USER.name())
            .eq(LLMCapabilityDefault::getOwnerUserId, userId)
            .eq(LLMCapabilityDefault::getCapability, normalized));
        return resolve(userId, normalized);
    }

    @Override
    @Transactional
    public void clearUserDefaultForConfig(Long userId, Long configId) {
        defaultMapper.delete(new LambdaQueryWrapper<LLMCapabilityDefault>()
            .eq(LLMCapabilityDefault::getScope, LLMConfigScope.USER.name())
            .eq(LLMCapabilityDefault::getOwnerUserId, userId)
            .eq(LLMCapabilityDefault::getConfigId, configId));
    }

    @Override
    @Transactional
    public void clearUserDefaultsForConfig(Long configId) {
        defaultMapper.delete(new LambdaQueryWrapper<LLMCapabilityDefault>()
            .eq(LLMCapabilityDefault::getScope, LLMConfigScope.USER.name())
            .eq(LLMCapabilityDefault::getConfigId, configId));
    }

    private CapabilityDefaultDTO resolve(Long userId, String capability) {
        LLMCapabilityDefault userDefault = find(LLMConfigScope.USER.name(), userId, capability);
        Long configId = userDefault == null ? null : userDefault.getConfigId();
        if (configId != null) {
            configValidator.requireExecutable(userId, configId, capability);
        }
        return new CapabilityDefaultDTO(capability, configId);
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

package com.qingluo.link.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据集向量模型绑定校验。
 *
 * <p>数据集级绑定固化配置来源与 ID，避免运行期继续按“当前默认模型”漂移。
 */
@Component
@RequiredArgsConstructor
public class DatasetEmbeddingConfigValidator {

    public static final String SPARSE_EMBEDDING = "SPARSE_EMBEDDING";
    public static final String EMBEDDING = "EMBEDDING";
    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_SYSTEM = "SYSTEM";
    private static final String LINKRAG_PROVIDER_TYPE = "linkrag";

    private final UserLLMConfigMapper userLLMConfigMapper;
    private final SystemPresetMapper systemPresetMapper;

    public void validateBindingPair(Long userId, Long sparseEmbeddingConfigId, Long denseEmbeddingConfigId) {
        validateAndResolveBindingPair(userId, sparseEmbeddingConfigId, null, denseEmbeddingConfigId, null);
    }

    public ResolvedBindingPair validateAndResolveBindingPair(Long userId,
                                                             Long sparseEmbeddingConfigId,
                                                             String sparseEmbeddingConfigSource,
                                                             Long denseEmbeddingConfigId,
                                                             String denseEmbeddingConfigSource) {
        ResolvedBinding sparse = requireActiveConfig(userId, sparseEmbeddingConfigId, sparseEmbeddingConfigSource,
            SPARSE_EMBEDDING, "稀疏向量模型配置");
        ResolvedBinding dense = requireActiveConfig(userId, denseEmbeddingConfigId, denseEmbeddingConfigSource,
            EMBEDDING, "稠密向量模型配置");
        return new ResolvedBindingPair(sparse, dense);
    }

    public void validateStoredBindings(Long userId, DatasetParseConfig config) {
        if (config == null
            || config.getSparseEmbeddingConfigId() == null
            || config.getDenseEmbeddingConfigId() == null) {
            throw new BusinessException(400, "数据集缺少稀疏/稠密向量模型绑定，请先补全解析配置", 400);
        }
        validateAndResolveBindingPair(userId,
            config.getSparseEmbeddingConfigId(), config.getSparseEmbeddingConfigSource(),
            config.getDenseEmbeddingConfigId(), config.getDenseEmbeddingConfigSource());
    }

    private ResolvedBinding requireActiveConfig(Long userId, Long configId, String source,
                                                String capability, String label) {
        if (configId == null) {
            throw new BusinessException(400, label + "不能为空", 400);
        }
        String normalizedSource = normalizeSource(source);
        if (normalizedSource == null || SOURCE_USER.equals(normalizedSource)) {
            UserLLMConfig userConfig = selectActiveUserConfig(userId, configId, capability);
            if (userConfig != null) {
                return new ResolvedBinding(configId, SOURCE_USER);
            }
        }
        if (normalizedSource == null || SOURCE_SYSTEM.equals(normalizedSource)) {
            SystemPreset systemPreset = selectActiveSystemPreset(configId, capability);
            if (systemPreset != null) {
                return new ResolvedBinding(configId, SOURCE_SYSTEM);
            }
        }
        throw new BusinessException(400, label + "不存在、未启用或能力不匹配", 400);
    }

    private UserLLMConfig selectActiveUserConfig(Long userId, Long configId, String capability) {
        return userLLMConfigMapper.selectOne(new LambdaQueryWrapper<UserLLMConfig>()
            .eq(UserLLMConfig::getId, configId)
            .eq(UserLLMConfig::getUserId, userId)
            .eq(UserLLMConfig::getCapability, capability)
            .eq(UserLLMConfig::getIsActive, true)
            .eq(UserLLMConfig::getIsSystemPreset, false)
            .last("LIMIT 1"));
    }

    private SystemPreset selectActiveSystemPreset(Long configId, String capability) {
        return systemPresetMapper.selectOne(new LambdaQueryWrapper<SystemPreset>()
            .eq(SystemPreset::getId, configId)
            .eq(SystemPreset::getProviderType, LINKRAG_PROVIDER_TYPE)
            .eq(SystemPreset::getCapability, capability)
            .eq(SystemPreset::getIsDefault, true)
            .eq(SystemPreset::getIsActive, true)
            .last("LIMIT 1"));
    }

    private String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_USER.equals(normalized) && !SOURCE_SYSTEM.equals(normalized)) {
            throw new BusinessException(400, "模型配置来源仅支持 USER/SYSTEM", 400);
        }
        return normalized;
    }

    public record ResolvedBinding(Long configId, String source) {
    }

    public record ResolvedBindingPair(ResolvedBinding sparse, ResolvedBinding dense) {
    }
}

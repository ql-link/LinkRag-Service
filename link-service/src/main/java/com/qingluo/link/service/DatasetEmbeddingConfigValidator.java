package com.qingluo.link.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据集向量模型绑定校验。
 *
 * <p>数据集级绑定使用 {@code llm_user_config.id}，避免运行期继续按“当前默认模型”漂移。
 */
@Component
@RequiredArgsConstructor
public class DatasetEmbeddingConfigValidator {

    public static final String SPARSE_EMBEDDING = "SPARSE_EMBEDDING";
    public static final String EMBEDDING = "EMBEDDING";

    private final UserLLMConfigMapper userLLMConfigMapper;

    public void validateBindingPair(Long userId, Long sparseEmbeddingConfigId, Long denseEmbeddingConfigId) {
        requireActiveConfig(userId, sparseEmbeddingConfigId, SPARSE_EMBEDDING, "稀疏向量模型配置");
        requireActiveConfig(userId, denseEmbeddingConfigId, EMBEDDING, "稠密向量模型配置");
    }

    public void validateStoredBindings(Long userId, DatasetParseConfig config) {
        if (config == null
            || config.getSparseEmbeddingConfigId() == null
            || config.getDenseEmbeddingConfigId() == null) {
            throw new BusinessException(400, "数据集缺少稀疏/稠密向量模型绑定，请先补全解析配置", 400);
        }
        validateBindingPair(userId, config.getSparseEmbeddingConfigId(), config.getDenseEmbeddingConfigId());
    }

    private UserLLMConfig requireActiveConfig(Long userId, Long configId, String capability, String label) {
        if (configId == null) {
            throw new BusinessException(400, label + "不能为空", 400);
        }
        UserLLMConfig config = userLLMConfigMapper.selectOne(new LambdaQueryWrapper<UserLLMConfig>()
            .eq(UserLLMConfig::getId, configId)
            .eq(UserLLMConfig::getUserId, userId)
            .eq(UserLLMConfig::getCapability, capability)
            .eq(UserLLMConfig::getIsActive, true)
            .eq(UserLLMConfig::getIsSystemPreset, false)
            .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(400, label + "不存在、未启用或能力不匹配", 400);
        }
        return config;
    }
}

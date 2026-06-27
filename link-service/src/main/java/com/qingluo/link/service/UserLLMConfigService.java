package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.SelectEffectiveModelRequest;
import com.qingluo.link.model.dto.request.SetupProviderRequest;
import com.qingluo.link.model.dto.request.ToggleModelRequest;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import java.util.List;

/**
 * 用户 LLM 配置服务接口。
 *
 * <p>两步配置：①配置厂商（选厂商 + 填厂商级 Key，自动展开该厂商全部模型能力行）；
 * ②按能力选生效模型。模型启停为独立动作。llm_user_config 只保存用户自配配置；
 * LinkRag 系统兜底以只读配置项形式返回给前端。</p>
 */
public interface UserLLMConfigService {

    /**
     * 获取用户可用配置，包含用户自配配置和 LinkRag 只读配置，支持按厂商/能力/启用状态过滤。
     */
    List<UserLLMConfigDTO> getConfigs(Long userId, String providerType, String capability, Boolean isActive);

    /**
     * 配置厂商（第一步）：校验厂商启用、加密厂商级 Key，按模型能力目录展开该厂商
     * 全部 (模型, 能力) 写入用户配置；重复配置同一厂商则更新其 Key。
     */
    List<UserLLMConfigDTO> setupProvider(Long userId, SetupProviderRequest request);

    /**
     * 模型启停（独立窗口）：按 (厂商, 模型) 批量切换该模型全部能力行的启用状态。
     */
    void toggleModel(Long userId, ToggleModelRequest request);

    /**
     * 按能力选生效模型（第二步）：把指定模型设为该能力生效。
     * providerType=linkrag 时清空用户自配默认并回退 LinkRag。
     */
    void selectEffectiveModel(Long userId, SelectEffectiveModelRequest request);

    /**
     * 删除用户自配配置。
     */
    void deleteConfig(Long userId, Long configId);

    /**
     * 获取用户自配默认配置（CHAT 能力），不含系统兜底。
     */
    UserLLMConfigDTO getDefaultConfig(Long userId);

    /**
     * 获取用户某个能力的自配默认配置，不含系统兜底。
     */
    UserLLMConfigDTO getDefaultConfig(Long userId, String capability);

    /**
     * 将用户的一条自配配置设为某能力生效。
     */
    void setDefaultConfig(Long userId, Long configId, String capability);

    /**
     * 清空用户某能力的自配默认配置，使有效配置解析回退到系统兜底。
     */
    void clearDefaultConfig(Long userId, String capability);
}

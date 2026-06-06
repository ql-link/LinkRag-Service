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
 * ②按能力选生效模型。模型启停为独立动作。系统预设与用户自配统一汇入本表，是下游唯一生效源。</p>
 */
public interface UserLLMConfigService {

    /**
     * 获取用户所有配置（含预设行），支持按厂商/能力/启用状态过滤。
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
     * 按能力选生效模型（第二步）：把指定自配模型设为该能力生效，自动解除原生效。
     */
    void selectEffectiveModel(Long userId, SelectEffectiveModelRequest request);

    /**
     * 删除配置；系统预设行只读，删除被拒。
     */
    void deleteConfig(Long userId, Long configId);

    /**
     * 获取用户默认配置（CHAT 能力）。
     */
    UserLLMConfigDTO getDefaultConfig(Long userId);

    /**
     * 获取用户某个能力的生效配置。
     */
    UserLLMConfigDTO getDefaultConfig(Long userId, String capability);

    /**
     * 将用户的一条配置设为某能力生效（含按能力切换到/切回系统预设）。
     */
    void setDefaultConfig(Long userId, Long configId, String capability);
}

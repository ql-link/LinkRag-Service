package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import java.util.List;

/**
 * 用户 LLM 配置服务接口
 */
public interface UserLLMConfigService {

    /**
     * 获取用户所有配置
     */
    List<UserLLMConfigDTO> getConfigs(Long userId, String providerType, String capability, Boolean isActive);

    /**
     * 创建指定能力的一条配置。
     */
    UserLLMConfigDTO createConfig(Long userId, CreateConfigRequest request);

    /**
     * 更新配置
     */
    void updateConfig(Long userId, Long configId, UpdateConfigRequest request);

    /**
     * 删除配置
     */
    void deleteConfig(Long userId, Long configId);

    /**
     * 获取用户默认配置（CHAT 能力）
     */
    UserLLMConfigDTO getDefaultConfig(Long userId);

    /**
     * 获取用户某个能力的默认配置
     */
    UserLLMConfigDTO getDefaultConfig(Long userId, String capability);

    /**
     * 设置用户某个能力的默认配置
     */
    void setDefaultConfig(Long userId, Long configId, String capability);
}

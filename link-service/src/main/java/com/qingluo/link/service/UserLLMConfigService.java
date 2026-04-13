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
    List<UserLLMConfigDTO> getConfigs(Long userId, String providerType, Boolean isActive);

    /**
     * 创建配置
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
     * 获取用户默认配置
     */
    UserLLMConfigDTO getDefaultConfig(Long userId);
}
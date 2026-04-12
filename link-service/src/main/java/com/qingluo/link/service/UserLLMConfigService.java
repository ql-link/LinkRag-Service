package com.qingluo.link.service;

import com.qingluo.link.core.dto.request.CreateConfigRequest;
import com.qingluo.link.core.dto.request.UpdateConfigRequest;
import com.qingluo.link.core.dto.response.UserLLMConfigDTO;

import java.util.List;

/**
 * 用户 LLM 配置服务接口
 */
public interface UserLLMConfigService {

    /**
     * 获取用户的所有 LLM 配置
     */
    List<UserLLMConfigDTO> listUserConfigs(String userId);

    /**
     * 获取用户的单个配置
     */
    UserLLMConfigDTO getUserConfig(String userId, String configId);

    /**
     * 获取用户的默认配置
     */
    UserLLMConfigDTO getDefaultConfig(String userId);

    /**
     * 创建用户配置
     */
    UserLLMConfigDTO createUserConfig(String userId, CreateConfigRequest request);

    /**
     * 更新用户配置
     */
    UserLLMConfigDTO updateUserConfig(String userId, String configId, UpdateConfigRequest request);

    /**
     * 删除用户配置
     */
    void deleteUserConfig(String userId, String configId);

    /**
     * 设为默认配置
     */
    void setDefaultConfig(String userId, String configId);
}
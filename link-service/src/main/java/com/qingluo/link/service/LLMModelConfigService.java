package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.AdminPlatformConfigSaveRequest;
import com.qingluo.link.model.dto.request.SetupProviderRequest;
import com.qingluo.link.model.dto.response.AdminPlatformConfigSaveResult;
import com.qingluo.link.model.dto.response.ExecutableLLMConfigDTO;
import com.qingluo.link.model.enums.LLMConfigMutationMode;
import java.util.List;

public interface LLMModelConfigService {

    List<ExecutableLLMConfigDTO> listVisibleConfigs(
        Long userId, String providerType, String capability, Boolean isActive);

    List<ExecutableLLMConfigDTO> listSystemConfigs(String capability, Boolean isActive);

    List<ExecutableLLMConfigDTO> setupProvider(Long userId, SetupProviderRequest request);

    AdminPlatformConfigSaveResult saveSystemConfig(Long configId, AdminPlatformConfigSaveRequest request);

    void changeActive(Long actorUserId, boolean admin, Long configId, boolean isActive,
                      LLMConfigMutationMode mode, boolean confirmed);

    void deleteConfig(Long actorUserId, boolean admin, Long configId);
}

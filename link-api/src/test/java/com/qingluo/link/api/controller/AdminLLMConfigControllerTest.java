package com.qingluo.link.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.qingluo.link.model.dto.request.AdminPlatformConfigSaveRequest;
import com.qingluo.link.model.dto.response.AdminPlatformConfigSaveResult;
import com.qingluo.link.model.dto.response.ExecutableLLMConfigDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.LLMModelConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminLLMConfigControllerTest {

    @Mock
    private LLMModelConfigService configService;
    @InjectMocks
    private AdminLLMConfigController controller;

    @Test
    void createUsesOneBusinessCommandForPlatformConfig() {
        AdminPlatformConfigSaveRequest request = new AdminPlatformConfigSaveRequest();
        request.setSourceProviderModelId(30L);
        request.setApiKey("secret");
        ExecutableLLMConfigDTO config = new ExecutableLLMConfigDTO();
        config.setConfigId(101L);
        AdminPlatformConfigSaveResult saved = new AdminPlatformConfigSaveResult(config);
        given(configService.saveSystemConfig(null, request)).willReturn(saved);

        Result<AdminPlatformConfigSaveResult> result = controller.createConfig(request);

        assertThat(result.getData().getConfig().getConfigId()).isEqualTo(101L);
        verify(configService).saveSystemConfig(null, request);
    }

    @Test
    void updateKeepsTheRequestedGlobalConfigId() {
        AdminPlatformConfigSaveRequest request = new AdminPlatformConfigSaveRequest();
        given(configService.saveSystemConfig(101L, request))
            .willReturn(new AdminPlatformConfigSaveResult());

        controller.updateConfig(101L, request);

        verify(configService).saveSystemConfig(101L, request);
    }
}

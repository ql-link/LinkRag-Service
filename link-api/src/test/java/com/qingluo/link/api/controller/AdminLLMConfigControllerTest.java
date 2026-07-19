package com.qingluo.link.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.qingluo.link.model.dto.request.AdminPlatformConfigSaveRequest;
import com.qingluo.link.model.dto.response.AdminPlatformConfigSaveResult;
import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import com.qingluo.link.model.dto.response.ExecutableLLMConfigDTO;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.LLMCapabilityDefaultService;
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
    @Mock
    private LLMCapabilityDefaultService defaultService;

    @InjectMocks
    private AdminLLMConfigController controller;

    @Test
    void createUsesOneAtomicBusinessCommandForConfigAndOptionalDefault() {
        AdminPlatformConfigSaveRequest request = new AdminPlatformConfigSaveRequest();
        request.setSourceProviderModelId(30L);
        request.setApiKey("secret");
        request.setSetAsDefault(true);
        ExecutableLLMConfigDTO config = new ExecutableLLMConfigDTO();
        config.setConfigId(101L);
        AdminPlatformConfigSaveResult saved = new AdminPlatformConfigSaveResult(
            config, new CapabilityDefaultDTO("CHAT", null, 101L, 101L));
        given(configService.saveSystemConfig(null, request)).willReturn(saved);

        Result<AdminPlatformConfigSaveResult> result = controller.createConfig(request);

        assertThat(result.getData().getConfig().getConfigId()).isEqualTo(101L);
        assertThat(result.getData().getCapabilityDefault().getEffectiveConfigId()).isEqualTo(101L);
        verify(configService).saveSystemConfig(null, request);
        verifyNoMoreInteractions(configService, defaultService);
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

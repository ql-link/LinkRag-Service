package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.request.CreateConfigRequest;
import com.qingluo.link.model.dto.request.UpdateConfigRequest;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import com.qingluo.link.service.UserLLMConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigController 控制器测试
 * TDD Red 阶段
 */
@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

    @Mock
    private UserLLMConfigService userLLMConfigService;

    @InjectMocks
    private ConfigController configController;

    @Test
    void Should_ReturnConfigList_When_GetConfigs() {
        // given
        Long userId = 1L;
        UserLLMConfigDTO config = new UserLLMConfigDTO();
        config.setId(1L);
        config.setConfigName("我的GPT-4");
        config.setProviderType("openai");
        config.setModelName("gpt-4");

        when(userLLMConfigService.getConfigs(eq(userId), any(), any()))
            .thenReturn(List.of(config));

        // when
        Result<List<UserLLMConfigDTO>> result = configController.getConfigs(null, null);

        // then
        assertNotNull(result);
        assertEquals(1, result.getData().size());
        assertEquals("我的GPT-4", result.getData().get(0).getConfigName());
        verify(userLLMConfigService).getConfigs(eq(userId), any(), any());
    }

    @Test
    void Should_ReturnCreatedConfig_When_CreateConfigSuccess() {
        // given
        Long userId = 1L;
        CreateConfigRequest request = new CreateConfigRequest();
        request.setProviderType("openai");
        request.setConfigName("我的GPT-4");
        request.setApiKey("sk-test123");
        request.setModelName("gpt-4");

        UserLLMConfigDTO created = new UserLLMConfigDTO();
        created.setId(1L);
        created.setConfigName("我的GPT-4");
        created.setProviderType("openai");

        when(userLLMConfigService.createConfig(eq(userId), any(CreateConfigRequest.class)))
            .thenReturn(created);

        // when
        Result<UserLLMConfigDTO> result = configController.createConfig(request);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getData().getId());
        assertEquals("我的GPT-4", result.getData().getConfigName());
        verify(userLLMConfigService).createConfig(eq(userId), any(CreateConfigRequest.class));
    }

    @Test
    void Should_ReturnOk_When_UpdateConfigSuccess() {
        // given
        Long userId = 1L;
        Long configId = 1L;
        UpdateConfigRequest request = new UpdateConfigRequest();
        request.setPriority(80);

        doNothing().when(userLLMConfigService).updateConfig(eq(userId), eq(configId), any(UpdateConfigRequest.class));

        // when
        Result<Void> result = configController.updateConfig(configId, request);

        // then
        assertNotNull(result);
        verify(userLLMConfigService).updateConfig(eq(userId), eq(configId), any(UpdateConfigRequest.class));
    }

    @Test
    void Should_ReturnOk_When_DeleteConfigSuccess() {
        // given
        Long userId = 1L;
        Long configId = 1L;

        doNothing().when(userLLMConfigService).deleteConfig(eq(userId), eq(configId));

        // when
        Result<Void> result = configController.deleteConfig(configId);

        // then
        assertNotNull(result);
        verify(userLLMConfigService).deleteConfig(eq(userId), eq(configId));
    }
}

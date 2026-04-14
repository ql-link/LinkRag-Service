package com.qingluo.link.api.controller;

import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.Result;
import com.qingluo.link.service.AdminProviderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminControllerProviderTest {

    @Mock
    private AdminProviderService adminProviderService;

    @InjectMocks
    private AdminController adminController;

    @Test
    @DisplayName("Should_ReturnProviderPage_When_ListProviders")
    void Should_ReturnProviderPage_When_ListProviders() {
        SystemProvider provider = new SystemProvider();
        provider.setId(1L);
        provider.setProviderType("openai");
        PageResult<SystemProvider> page = new PageResult<>(List.of(provider), 1, 1, 10);
        org.mockito.BDDMockito.given(adminProviderService.listProviders(1, 10)).willReturn(page);

        Result<PageResult<SystemProvider>> result = adminController.listProviders(1, 10);

        assertThat(result.getData().getTotal()).isEqualTo(1);
        verify(adminProviderService).listProviders(1, 10);
    }

    @Test
    @DisplayName("Should_DelegateToService_When_CreateProvider")
    void Should_DelegateToService_When_CreateProvider() {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setProviderType("openai");
        request.setProviderName("OpenAI");
        request.setApiBaseUrl("https://api.openai.com/v1");
        request.setIsActive(true);
        request.setPriority(100);

        Result<Void> result = adminController.createProvider(request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(adminProviderService).createProvider(request);
    }

    @Test
    @DisplayName("Should_DelegateToService_When_UpdateProvider")
    void Should_DelegateToService_When_UpdateProvider() {
        UpdateProviderRequest request = new UpdateProviderRequest();
        request.setProviderName("OpenAI Updated");

        Result<Void> result = adminController.updateProvider(1L, request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(adminProviderService).updateProvider(eq(1L), any(UpdateProviderRequest.class));
    }

    @Test
    @DisplayName("Should_DelegateToService_When_DeleteProvider")
    void Should_DelegateToService_When_DeleteProvider() {
        Result<Void> result = adminController.deleteProvider(1L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(adminProviderService).deleteProvider(1L);
    }

    @Test
    @DisplayName("Should_DelegateToService_When_ToggleActive")
    void Should_DelegateToService_When_ToggleActive() {
        Result<Void> result = adminController.toggleProviderActive(1L, false);

        assertThat(result.getCode()).isEqualTo(200);
        verify(adminProviderService).toggleActive(1L, false);
    }
}

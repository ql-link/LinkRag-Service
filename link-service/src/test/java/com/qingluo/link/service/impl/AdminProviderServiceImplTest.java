package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMProtocolService;
import com.qingluo.link.service.impl.admin.AdminProviderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminProviderServiceImplTest {

    @Mock
    private SystemProviderMapper systemProviderMapper;

    @Mock
    private ProviderModelMapper providerModelMapper;

    @Mock
    private CacheConsistencyService cacheConsistencyService;
    @Mock
    private LLMProtocolService llmProtocolService;

    @InjectMocks
    private AdminProviderServiceImpl adminProviderService;

    // ---- listProviders ----

    @Test
    @DisplayName("Should_ReturnPageOfProviders_When_ListProviders")
    void Should_ReturnPageOfProviders_When_ListProviders() {
        SystemProvider provider = buildProvider(1L, "openai", true);
        IPage<SystemProvider> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(provider));
        mockPage.setTotal(1);
        given(systemProviderMapper.selectPage(any(), any())).willReturn(mockPage);

        PageResult<SystemProvider> result = adminProviderService.listProviders(1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems().get(0).getProviderType()).isEqualTo("openai");
    }

    // ---- createProvider ----

    @Test
    @DisplayName("Should_InsertAndEvictCache_When_CreateProvider")
    void Should_InsertAndEvictCache_When_CreateProvider() {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setProviderType("openai");
        request.setProviderName("OpenAI");
        request.setIconUrl("https://minio.example/tolink-public/providerIcon/openai.png");
        request.setIconObjectKey("providerIcon/openai.png");
        request.setApiBaseUrl("https://api.openai.com/v1");
        request.setDefaultProtocol("openai");
        request.setIsActive(false);
        request.setPriority(100);
        given(systemProviderMapper.selectCount(any())).willReturn(0L);

        adminProviderService.createProvider(request);

        ArgumentCaptor<SystemProvider> captor = ArgumentCaptor.forClass(SystemProvider.class);
        verify(systemProviderMapper).insert(captor.capture());
        assertThat(captor.getValue().getIconUrl())
                .isEqualTo("https://minio.example/tolink-public/providerIcon/openai.png");
        assertThat(captor.getValue().getIconObjectKey()).isEqualTo("providerIcon/openai.png");
        verify(cacheConsistencyService).evict(CacheEvictTarget.SYSTEM_PROVIDER, "openai");
    }

    @Test
    @DisplayName("Should_RejectActiveProviderCreation_When_NoActiveModelCanExist")
    void Should_RejectActiveProviderCreation_When_NoActiveModelCanExist() {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setProviderType("minimax");
        request.setProviderName("MiniMax");
        request.setApiBaseUrl("https://api.minimaxi.com/v1");
        request.setDefaultProtocol("openai");
        request.setIsActive(true);
        request.setPriority(50);
        given(systemProviderMapper.selectCount(any())).willReturn(0L);

        assertThatThrownBy(() -> adminProviderService.createProvider(request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PROVIDER_HAS_NO_ACTIVE_MODEL.getCode()));

        verify(systemProviderMapper, never()).insert(any(SystemProvider.class));
        verify(cacheConsistencyService, never()).evict(any(), any());
    }

    @Test
    @DisplayName("Should_ThrowConflictException_When_ProviderTypeDuplicate")
    void Should_ThrowConflictException_When_ProviderTypeDuplicate() {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setProviderType("openai");
        request.setDefaultProtocol("openai");
        given(systemProviderMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> adminProviderService.createProvider(request))
                .isInstanceOf(BusinessException.class);
    }

    // ---- updateProvider ----

    @Test
    @DisplayName("Should_UpdateAndEvictCache_When_ProviderExists")
    void Should_UpdateAndEvictCache_When_ProviderExists() {
        SystemProvider existing = buildProvider(1L, "openai", true);
        given(systemProviderMapper.selectById(1L)).willReturn(existing);

        UpdateProviderRequest request = new UpdateProviderRequest();
        request.setProviderName("OpenAI Updated");
        request.setIconUrl("https://minio.example/tolink-public/providerIcon/openai-new.png");
        request.setIconObjectKey("providerIcon/openai-new.png");
        request.setDefaultProtocol("openai");
        request.setPriority(80);
        adminProviderService.updateProvider(1L, request);

        ArgumentCaptor<SystemProvider> captor = ArgumentCaptor.forClass(SystemProvider.class);
        verify(systemProviderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIconUrl())
                .isEqualTo("https://minio.example/tolink-public/providerIcon/openai-new.png");
        assertThat(captor.getValue().getIconObjectKey()).isEqualTo("providerIcon/openai-new.png");
        verify(cacheConsistencyService).evict(CacheEvictTarget.SYSTEM_PROVIDER, "openai");
    }

    @Test
    @DisplayName("Should_RejectProviderUpdateToActive_When_NoActiveModel")
    void Should_RejectProviderUpdateToActive_When_NoActiveModel() {
        SystemProvider existing = buildProvider(1L, "minimax", false);
        given(systemProviderMapper.selectById(1L)).willReturn(existing);
        given(providerModelMapper.selectCount(any())).willReturn(0L);

        UpdateProviderRequest request = new UpdateProviderRequest();
        request.setIsActive(true);

        assertThatThrownBy(() -> adminProviderService.updateProvider(1L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PROVIDER_HAS_NO_ACTIVE_MODEL.getCode()));

        verify(systemProviderMapper, never()).updateById(any(SystemProvider.class));
        verify(cacheConsistencyService, never()).evict(any(), any());
    }

    @Test
    @DisplayName("Should_ThrowNotFoundException_When_UpdateProviderNotFound")
    void Should_ThrowNotFoundException_When_UpdateProviderNotFound() {
        given(systemProviderMapper.selectById(99L)).willReturn(null);

        UpdateProviderRequest request = new UpdateProviderRequest();
        request.setProviderName("New Name");

        assertThatThrownBy(() -> adminProviderService.updateProvider(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- deleteProvider ----

    @Test
    @DisplayName("Should_DeleteAndEvictCache_When_ProviderExists")
    void Should_DeleteAndEvictCache_When_ProviderExists() {
        SystemProvider existing = buildProvider(1L, "openai", true);
        given(systemProviderMapper.selectById(1L)).willReturn(existing);

        adminProviderService.deleteProvider(1L);

        verify(systemProviderMapper).deleteById(1L);
        verify(cacheConsistencyService).evict(CacheEvictTarget.SYSTEM_PROVIDER, "openai");
    }

    @Test
    @DisplayName("Should_ThrowNotFoundException_When_DeleteProviderNotFound")
    void Should_ThrowNotFoundException_When_DeleteProviderNotFound() {
        given(systemProviderMapper.selectById(99L)).willReturn(null);

        assertThatThrownBy(() -> adminProviderService.deleteProvider(99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- toggleActive ----

    @Test
    @DisplayName("Should_ToggleActiveAndEvictCache")
    void Should_ToggleActiveAndEvictCache() {
        SystemProvider existing = buildProvider(1L, "openai", true);
        given(systemProviderMapper.selectById(1L)).willReturn(existing);

        adminProviderService.toggleActive(1L, false);

        verify(systemProviderMapper).updateById(any(SystemProvider.class));
        verify(cacheConsistencyService).evict(CacheEvictTarget.SYSTEM_PROVIDER, "openai");
    }

    @Test
    @DisplayName("Should_EnableProviderAndEvictCache_When_ActiveModelExists")
    void Should_EnableProviderAndEvictCache_When_ActiveModelExists() {
        SystemProvider existing = buildProvider(1L, "openai", false);
        given(systemProviderMapper.selectById(1L)).willReturn(existing);
        given(providerModelMapper.selectCount(any())).willReturn(1L);

        adminProviderService.toggleActive(1L, true);

        ArgumentCaptor<SystemProvider> captor = ArgumentCaptor.forClass(SystemProvider.class);
        verify(systemProviderMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsActive()).isTrue();
        verify(cacheConsistencyService).evict(CacheEvictTarget.SYSTEM_PROVIDER, "openai");
    }

    @Test
    @DisplayName("Should_RejectProviderActivation_When_NoActiveModel")
    void Should_RejectProviderActivation_When_NoActiveModel() {
        SystemProvider existing = buildProvider(1L, "minimax", false);
        given(systemProviderMapper.selectById(1L)).willReturn(existing);
        given(providerModelMapper.selectCount(any())).willReturn(0L);

        assertThatThrownBy(() -> adminProviderService.toggleActive(1L, true))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.PROVIDER_HAS_NO_ACTIVE_MODEL.getCode()));

        verify(systemProviderMapper, never()).updateById(any(SystemProvider.class));
        verify(cacheConsistencyService, never()).evict(any(), any());
    }

    // ---- helpers ----

    private SystemProvider buildProvider(Long id, String type, boolean active) {
        SystemProvider p = new SystemProvider();
        p.setId(id);
        p.setProviderType(type);
        p.setProviderName("Test Provider");
        p.setIconUrl("https://minio.example/tolink-public/providerIcon/" + type + ".png");
        p.setIconObjectKey("providerIcon/" + type + ".png");
        p.setApiBaseUrl("https://test.com/v1");
        p.setIsActive(active);
        p.setPriority(50);
        return p;
    }
}

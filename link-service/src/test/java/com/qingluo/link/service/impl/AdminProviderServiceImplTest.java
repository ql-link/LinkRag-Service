package com.qingluo.link.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.components.redis.service.DoubleDeleteCacheService;
import com.qingluo.link.service.impl.admin.AdminProviderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminProviderServiceImplTest {

    @Mock
    private SystemProviderMapper systemProviderMapper;

    @Mock
    private DoubleDeleteCacheService doubleDeleteCacheService;

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
        request.setApiBaseUrl("https://api.openai.com/v1");
        request.setIsActive(true);
        request.setPriority(100);
        given(systemProviderMapper.selectCount(any())).willReturn(0L);

        adminProviderService.createProvider(request);

        verify(systemProviderMapper).insert(any(SystemProvider.class));
        verify(doubleDeleteCacheService).evictProviderCache("openai");
    }

    @Test
    @DisplayName("Should_ThrowConflictException_When_ProviderTypeDuplicate")
    void Should_ThrowConflictException_When_ProviderTypeDuplicate() {
        CreateProviderRequest request = new CreateProviderRequest();
        request.setProviderType("openai");
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
        request.setPriority(80);
        adminProviderService.updateProvider(1L, request);

        verify(systemProviderMapper).updateById(any(SystemProvider.class));
        verify(doubleDeleteCacheService).evictProviderCache("1");
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
        verify(doubleDeleteCacheService).evictProviderCache("1");
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
        verify(doubleDeleteCacheService).evictProviderCache("1");
    }

    // ---- helpers ----

    private SystemProvider buildProvider(Long id, String type, boolean active) {
        SystemProvider p = new SystemProvider();
        p.setId(id);
        p.setProviderType(type);
        p.setProviderName("Test Provider");
        p.setApiBaseUrl("https://test.com/v1");
        p.setIsActive(active);
        p.setPriority(50);
        return p;
    }
}

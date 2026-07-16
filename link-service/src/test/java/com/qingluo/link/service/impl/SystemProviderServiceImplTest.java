package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.response.ProviderModelDTO;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.impl.llm.SystemProviderServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemProviderServiceImplTest {

    @Mock private SystemProviderMapper systemProviderMapper;
    @Mock private LLMCapabilityService llmCapabilityService;
    @Mock private ProviderModelService providerModelService;
    @InjectMocks private SystemProviderServiceImpl service;

    @Test
    void getActiveProviderModels_queriesDatabaseAndFiltersCurrentRows() {
        SystemProvider openai = provider(5L, "openai");
        SystemProvider linkrag = provider(6L, "linkrag");
        given(systemProviderMapper.selectList(any())).willReturn(List.of(openai, linkrag));
        given(providerModelService.listActiveModelsByProviderIds(List.of(5L), "CHAT"))
            .willReturn(List.of(model(5L, "new-model", "CHAT")));

        List<ProviderModelDTO> result = service.getActiveProviderModels("chat");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProviderType()).isEqualTo("openai");
        assertThat(result.get(0).getModels()).extracting("modelName").containsExactly("new-model");
        verify(providerModelService).listActiveModelsByProviderIds(List.of(5L), "CHAT");
        verify(providerModelService, never()).listActiveModels(any(), any());
    }

    @Test
    void getActiveProviderModels_repeatsDatabaseQueries() {
        given(systemProviderMapper.selectList(any())).willReturn(List.of(provider(5L, "openai")));
        given(providerModelService.listActiveModelsByProviderIds(List.of(5L), null))
            .willReturn(List.of(model(5L, "model", "CHAT")));

        service.getActiveProviderModels(null);
        service.getActiveProviderModels(null);

        verify(systemProviderMapper, times(2)).selectList(any());
        verify(providerModelService, times(2)).listActiveModelsByProviderIds(List.of(5L), null);
    }

    @Test
    void getActiveProviderModels_shortCircuitsWithoutProviders() {
        given(systemProviderMapper.selectList(any())).willReturn(List.of());

        assertThat(service.getActiveProviderModels(null)).isEmpty();
        verify(providerModelService, never()).listActiveModelsByProviderIds(any(), any());
    }

    private SystemProvider provider(Long id, String type) {
        SystemProvider p = new SystemProvider();
        p.setId(id);
        p.setProviderType(type);
        p.setProviderName(type);
        p.setIconUrl(type + ".png");
        p.setIsActive(true);
        return p;
    }

    private ProviderModel model(Long providerId, String name, String capability) {
        ProviderModel m = new ProviderModel();
        m.setProviderId(providerId);
        m.setModelName(name);
        m.setDisplayName(name);
        m.setCapability(capability);
        m.setProtocol("openai");
        m.setApiBaseUrl("https://example.test/v1/chat/completions");
        m.setIsActive(true);
        return m;
    }
}

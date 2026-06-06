package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.response.ProviderModelDTO;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.impl.llm.SystemProviderServiceImpl;
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
import static org.mockito.BDDMockito.given;

/**
 * {@link SystemProviderServiceImpl} 单元测试，承接 acceptance 一类（按能力聚合目录、空厂商过滤）。
 */
@ExtendWith(MockitoExtension.class)
class SystemProviderServiceImplTest {

    @Mock
    private SystemProviderMapper systemProviderMapper;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private ProviderModelService providerModelService;

    @InjectMocks
    private SystemProviderServiceImpl service;

    @Test
    @DisplayName("一·按能力查询聚合返回该能力下的模型集合")
    void getActiveProviderModels_aggregatesByCapability() {
        given(systemProviderMapper.selectList(any())).willReturn(List.of(provider(5L, "openai")));
        given(providerModelService.listActiveModels(eq(5L), any()))
                .willReturn(List.of(pm("gpt-4o", "CHAT"), pm("gpt-4o-mini", "CHAT")));

        List<ProviderModelDTO> result = service.getActiveProviderModels("CHAT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModels()).extracting("modelName")
                .containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini");
    }

    @Test
    @DisplayName("一·过滤后无可选模型的厂商不返回")
    void getActiveProviderModels_dropsEmptyProvider() {
        given(systemProviderMapper.selectList(any())).willReturn(List.of(provider(5L, "openai"), provider(6L, "deepseek")));
        given(providerModelService.listActiveModels(eq(5L), any()))
                .willReturn(List.of(pm("text-embedding-3", "EMBEDDING")));
        given(providerModelService.listActiveModels(eq(6L), any())).willReturn(List.of());

        List<ProviderModelDTO> result = service.getActiveProviderModels("EMBEDDING");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProviderType()).isEqualTo("openai");
    }

    @Test
    @DisplayName("一·一个模型的多种能力聚合为能力列表")
    void getActiveProviderModels_groupsMultiCapability() {
        given(systemProviderMapper.selectList(any())).willReturn(List.of(provider(5L, "openai")));
        given(providerModelService.listActiveModels(eq(5L), any()))
                .willReturn(List.of(pm("gpt-4o", "CHAT"), pm("gpt-4o", "VISION"), pm("gpt-4o", "OCR")));

        List<ProviderModelDTO> result = service.getActiveProviderModels(null);

        assertThat(result.get(0).getModels()).hasSize(1);
        assertThat(result.get(0).getModels().get(0).getCapabilities())
                .containsExactlyInAnyOrder("CHAT", "VISION", "OCR");
    }

    private SystemProvider provider(Long id, String type) {
        SystemProvider provider = new SystemProvider();
        provider.setId(id);
        provider.setProviderType(type);
        provider.setProviderName(type);
        provider.setIsActive(true);
        provider.setPriority(50);
        return provider;
    }

    private ProviderModel pm(String model, String capability) {
        ProviderModel m = new ProviderModel();
        m.setModelName(model);
        m.setCapability(capability);
        m.setIsActive(true);
        return m;
    }
}

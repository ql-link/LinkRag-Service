package com.qingluo.link.service.impl;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.impl.llm.ProviderModelServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ProviderModelServiceImpl} 单元测试，承接 acceptance 一类（目录查询与管理端维护）。
 */
@ExtendWith(MockitoExtension.class)
class ProviderModelServiceImplTest {

    @Mock
    private ProviderModelMapper providerModelMapper;
    @Mock
    private SystemProviderMapper systemProviderMapper;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private CacheConsistencyService cacheConsistencyService;

    @InjectMocks
    private ProviderModelServiceImpl service;

    @Test
    @DisplayName("一·按能力查询返回该能力的上架模型行")
    void listActiveModels_filtersByCapability() {
        given(providerModelMapper.selectList(any())).willReturn(List.of(pm(1L, "gpt-4o", "CHAT")));

        List<ProviderModel> result = service.listActiveModels(5L, "CHAT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("isModelCapabilityActive 命中上架行返回 true")
    void isModelCapabilityActive_true() {
        given(providerModelMapper.selectCount(any())).willReturn(1L);
        assertThat(service.isModelCapabilityActive(5L, "gpt-4o", "CHAT")).isTrue();
    }

    @Test
    @DisplayName("一·管理员新增模型能力后目录新增一行（用户目录即时反映）")
    void addModelCapability_insertsNew() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "openai"));
        given(providerModelMapper.selectOne(any())).willReturn(null);

        ProviderModel result = service.addModelCapability(5L, "gpt-4o-realtime", "CHAT");

        verify(providerModelMapper).insert(any(ProviderModel.class));
        assertThat(result.getModelName()).isEqualTo("gpt-4o-realtime");
        verify(cacheConsistencyService).evict(eq(CacheEvictTarget.SYSTEM_PROVIDER), any());
    }

    @Test
    @DisplayName("新增已存在的模型能力幂等确保上架，不重复插入")
    void addModelCapability_idempotentReactivate() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "openai"));
        ProviderModel existing = pm(9L, "gpt-4o", "CHAT");
        existing.setIsActive(false);
        given(providerModelMapper.selectOne(any())).willReturn(existing);

        service.addModelCapability(5L, "gpt-4o", "CHAT");

        verify(providerModelMapper, never()).insert(any());
        verify(providerModelMapper).updateById(existing);
        assertThat(existing.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("向不存在的厂商新增模型能力被拒")
    void addModelCapability_rejectsUnknownProvider() {
        given(systemProviderMapper.selectById(99L)).willReturn(null);

        assertThatThrownBy(() -> service.addModelCapability(99L, "m", "CHAT"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("删除模型能力目录项")
    void deleteModelCapability() {
        given(providerModelMapper.selectById(9L)).willReturn(pm(9L, "gpt-4o", "CHAT"));
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "openai"));

        service.deleteModelCapability(9L);

        verify(providerModelMapper).deleteById(9L);
    }

    @Test
    @DisplayName("批量按厂商 ID 查上架模型，一次性取回多个厂商的行")
    void listActiveModelsByProviderIds_returnsForAllIds() {
        given(providerModelMapper.selectList(any()))
                .willReturn(List.of(pm(1L, "gpt-4o", "CHAT"), pm(2L, "deepseek-chat", "CHAT")));

        List<ProviderModel> result = service.listActiveModelsByProviderIds(List.of(5L, 6L), "CHAT");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("批量查空 ID 列表直接返回空且不查库")
    void listActiveModelsByProviderIds_emptyIdsShortCircuits() {
        List<ProviderModel> result = service.listActiveModelsByProviderIds(List.of(), "CHAT");

        assertThat(result).isEmpty();
        verify(providerModelMapper, never()).selectList(any());
    }

    private SystemProvider provider(Long id, String type) {
        SystemProvider p = new SystemProvider();
        p.setId(id);
        p.setProviderType(type);
        return p;
    }

    private ProviderModel pm(Long id, String model, String capability) {
        ProviderModel m = new ProviderModel();
        m.setId(id);
        m.setProviderId(5L);
        m.setModelName(model);
        m.setCapability(capability);
        m.setIsActive(true);
        return m;
    }
}

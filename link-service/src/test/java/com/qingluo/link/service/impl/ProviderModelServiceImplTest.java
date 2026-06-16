package com.qingluo.link.service.impl;

import com.qingluo.link.components.redis.service.CacheConsistencyService;
import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.core.exception.NotFoundException;
import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.LLMProtocolService;
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
import static org.mockito.Mockito.doThrow;
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
    private LLMProtocolService llmProtocolService;
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
    @DisplayName("模型能力改特殊协议保存为事实（写入 protocol/api_base_url）")
    void addModelCapability_insertsWithFacts() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "aliyun"));
        given(providerModelMapper.selectOne(any())).willReturn(null);

        ProviderModel result = service.addModelCapability(
                5L, "gte-rerank", "RERANK", "dashscope", "https://dashscope.aliyuncs.com/api/v1");

        verify(providerModelMapper).insert(any(ProviderModel.class));
        assertThat(result.getProtocol()).isEqualTo("dashscope");
        assertThat(result.getApiBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/api/v1");
        verify(cacheConsistencyService).evict(eq(CacheEvictTarget.SYSTEM_PROVIDER), any());
    }

    @Test
    @DisplayName("新增已存在的模型能力幂等确保上架并刷新事实字段")
    void addModelCapability_idempotentReactivateRefreshesFacts() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "openai"));
        ProviderModel existing = pm(9L, "gpt-4o", "CHAT");
        existing.setIsActive(false);
        given(providerModelMapper.selectOne(any())).willReturn(existing);

        service.addModelCapability(5L, "gpt-4o", "CHAT", "openai", "https://api.openai.com/v1");

        verify(providerModelMapper, never()).insert(any());
        verify(providerModelMapper).updateById(existing);
        assertThat(existing.getIsActive()).isTrue();
        assertThat(existing.getProtocol()).isEqualTo("openai");
        assertThat(existing.getApiBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    @DisplayName("向不存在的厂商新增模型能力被拒")
    void addModelCapability_rejectsUnknownProvider() {
        given(systemProviderMapper.selectById(99L)).willReturn(null);

        assertThatThrownBy(() -> service.addModelCapability(99L, "m", "CHAT", "openai", "https://x"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("缺入口阻断保存（MODEL_CONFIG_INCOMPLETE）")
    void addModelCapability_rejectsBlankApiBaseUrl() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "openai"));

        assertThatThrownBy(() -> service.addModelCapability(5L, "m", "CHAT", "openai", "  "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.MODEL_CONFIG_INCOMPLETE.getCode());
        verify(providerModelMapper, never()).insert(any());
    }

    @Test
    @DisplayName("非法协议拒绝保存（校验前置，不落库）")
    void addModelCapability_rejectsInvalidProtocol() {
        given(systemProviderMapper.selectById(5L)).willReturn(provider(5L, "openai"));
        doThrow(new BusinessException(ErrorCode.INVALID_PROTOCOL))
                .when(llmProtocolService).validateProtocol("cohere");

        assertThatThrownBy(() -> service.addModelCapability(5L, "m", "RERANK", "cohere", "https://x"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_PROTOCOL.getCode());
        verify(providerModelMapper, never()).insert(any());
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
        m.setProtocol("openai");
        m.setApiBaseUrl("https://api.openai.com/v1");
        m.setIsActive(true);
        return m;
    }
}

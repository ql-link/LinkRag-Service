package com.qingluo.link.service.impl;

import com.qingluo.link.mapper.ProviderModelMapper;
import com.qingluo.link.mapper.ProviderModelSyncCandidateMapper;
import com.qingluo.link.mapper.ProviderModelSyncJobMapper;
import com.qingluo.link.mapper.SystemProviderMapper;
import com.qingluo.link.model.dto.entity.ProviderModel;
import com.qingluo.link.model.dto.entity.ProviderModelSyncCandidate;
import com.qingluo.link.model.dto.entity.ProviderModelSyncJob;
import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.PublishModelSyncCandidateRequest;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.service.LLMCapabilityService;
import com.qingluo.link.service.ProviderModelService;
import com.qingluo.link.service.impl.llm.ProviderModelSyncServiceImpl;
import com.qingluo.link.service.llm.catalog.ExternalModelCatalogClient;
import com.qingluo.link.service.llm.catalog.ExternalModelCatalogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link ProviderModelSyncServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ProviderModelSyncServiceImplTest {

    @Mock
    private ProviderModelSyncJobMapper syncJobMapper;
    @Mock
    private ProviderModelSyncCandidateMapper syncCandidateMapper;
    @Mock
    private ProviderModelMapper providerModelMapper;
    @Mock
    private SystemProviderMapper systemProviderMapper;
    @Mock
    private LLMCapabilityService llmCapabilityService;
    @Mock
    private ProviderModelService providerModelService;
    @Mock
    private ExternalModelCatalogClient catalogClient;

    private ProviderModelSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProviderModelSyncServiceImpl(syncJobMapper, syncCandidateMapper, providerModelMapper,
                systemProviderMapper, llmCapabilityService, providerModelService, List.of(catalogClient));
    }

    @Test
    @DisplayName("刷新外部目录：写入候选并统计新增/匹配/过期数量")
    void refreshProviderModels_writesCandidatesAndStats() {
        given(catalogClient.source()).willReturn("MODELS_DEV");
        given(systemProviderMapper.selectById(5L)).willReturn(provider());
        doAnswer(inv -> {
            ProviderModelSyncJob job = inv.getArgument(0);
            job.setId(9L);
            return 1;
        }).when(syncJobMapper).insert(any(ProviderModelSyncJob.class));
        given(providerModelMapper.selectList(any()))
                .willReturn(List.of(model(11L, "gpt-4o", "CHAT")));
        given(catalogClient.listModels(any())).willReturn(List.of(
                external("gpt-4o", "GPT-4o", List.of("CHAT", "VISION")),
                external("gpt-new", "GPT New", List.of("CHAT"))));
        ArgumentCaptor<ProviderModelSyncCandidate> candidateCaptor =
                ArgumentCaptor.forClass(ProviderModelSyncCandidate.class);

        ProviderModelSyncJob job = service.refreshProviderModels(5L, null);

        assertThat(job.getStatus()).isEqualTo("SUCCESS");
        assertThat(job.getAddedCount()).isEqualTo(2);
        assertThat(job.getUpdatedCount()).isEqualTo(1);
        assertThat(job.getStaleCount()).isEqualTo(0);
        verify(syncCandidateMapper, times(3)).insert(candidateCaptor.capture());
        assertThat(candidateCaptor.getAllValues())
                .extracting(ProviderModelSyncCandidate::getInferredCapability)
                .containsExactly("CHAT", "VISION", "CHAT");
        assertThat(candidateCaptor.getAllValues())
                .extracting(ProviderModelSyncCandidate::getCapability)
                .containsExactly("CHAT", "VISION", "CHAT");
        assertThat(candidateCaptor.getAllValues())
                .extracting(ProviderModelSyncCandidate::getReleaseDate)
                .containsOnly(LocalDate.of(2025, 6, 17));
    }

    @Test
    @DisplayName("发布候选：复用正式目录服务写入 llm_provider_model 并标记 PUBLISHED")
    void publishCandidate_publishesToProviderModel() {
        ProviderModelSyncCandidate candidate = new ProviderModelSyncCandidate();
        candidate.setId(20L);
        candidate.setProviderId(5L);
        candidate.setModelName("gpt-new");
        candidate.setDisplayName("GPT New");
        candidate.setInferredCapability("CHAT");
        candidate.setInferredProtocol("openai");
        candidate.setInferredApiBaseUrl("https://api.openai.com/v1/chat/completions");
        given(syncCandidateMapper.selectById(20L)).willReturn(candidate);
        ProviderModel published = model(31L, "gpt-new", "CHAT");
        given(providerModelService.addModelCapability(5L, "gpt-new", "GPT New", "CHAT",
                "openai", "https://api.openai.com/v1/chat/completions")).willReturn(published);

        ProviderModel result = service.publishCandidate(20L, new PublishModelSyncCandidateRequest());

        assertThat(result.getId()).isEqualTo(31L);
        assertThat(candidate.getReviewStatus()).isEqualTo("PUBLISHED");
        assertThat(candidate.getMatchedProviderModelId()).isEqualTo(31L);
        verify(syncCandidateMapper).updateById(candidate);
    }

    @Test
    @DisplayName("审核候选：支持标记为 REJECTED")
    void updateReviewStatus_rejectsCandidate() {
        ProviderModelSyncCandidate candidate = new ProviderModelSyncCandidate();
        candidate.setId(20L);
        candidate.setReviewStatus("PENDING");
        given(syncCandidateMapper.selectById(20L)).willReturn(candidate);

        ProviderModelSyncCandidate result = service.updateReviewStatus(20L, "rejected");

        assertThat(result.getReviewStatus()).isEqualTo("REJECTED");
        verify(syncCandidateMapper).updateById(candidate);
    }

    @Test
    @DisplayName("候选列表支持按能力过滤，并归一化为大写能力")
    void listCandidates_filtersByCapability() {
        Page<ProviderModelSyncCandidate> page = new Page<>(1, 10);
        page.setTotal(0L);
        page.setRecords(List.of());
        given(syncCandidateMapper.selectPage(any(), any())).willReturn(page);

        PageResult<ProviderModelSyncCandidate> result =
                service.listCandidates(1, 10, 5L, null, "PENDING", "chat");

        assertThat(result.getTotal()).isZero();
        verify(llmCapabilityService).validateCapability("CHAT");
        verify(syncCandidateMapper).selectPage(any(), any());
    }

    private SystemProvider provider() {
        SystemProvider provider = new SystemProvider();
        provider.setId(5L);
        provider.setProviderType("openai");
        provider.setApiBaseUrl("https://api.openai.com/v1");
        return provider;
    }

    private ProviderModel model(Long id, String modelName, String capability) {
        ProviderModel model = new ProviderModel();
        model.setId(id);
        model.setProviderId(5L);
        model.setModelName(modelName);
        model.setCapability(capability);
        model.setProtocol("openai");
        model.setApiBaseUrl("https://api.openai.com/v1/chat/completions");
        model.setIsActive(true);
        return model;
    }

    private ExternalModelCatalogEntry external(String modelName, String displayName, List<String> capabilities) {
        ExternalModelCatalogEntry entry = new ExternalModelCatalogEntry();
        entry.setExternalModelId(modelName);
        entry.setModelName(modelName);
        entry.setDisplayName(displayName);
        entry.setCapabilities(capabilities);
        entry.setInputModalitiesJson("[\"text\"]");
        entry.setOutputModalitiesJson("[\"text\"]");
        entry.setContextWindow(128000);
        entry.setMaxOutputTokens(16000);
        entry.setReleaseDate(LocalDate.of(2025, 6, 17));
        entry.setRawMetadataJson("{}");
        return entry;
    }
}

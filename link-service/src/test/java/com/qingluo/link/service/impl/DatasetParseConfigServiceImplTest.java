package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetParseConfigMapper;
import com.qingluo.link.model.dto.config.ChunkingConfig;
import com.qingluo.link.model.dto.config.RecallConfig;
import com.qingluo.link.model.dto.entity.DatasetParseConfig;
import com.qingluo.link.model.dto.request.UpdateDatasetParseConfigRequest;
import com.qingluo.link.model.dto.response.DatasetParseConfigResponse;
import com.qingluo.link.service.DatasetEmbeddingConfigValidator;
import com.qingluo.link.service.DatasetService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 配置管理 Service 单测：归属校验（404）、读命中/未命中、写 insert/update 分支与召回新增项默认/校验。
 * 无 MyBatis 启动扫描，手动初始化 MP TableInfo，使实体可用于 LambdaQueryWrapper。
 */
@ExtendWith(MockitoExtension.class)
class DatasetParseConfigServiceImplTest {

    @Mock
    private DatasetParseConfigMapper datasetParseConfigMapper;

    @Mock
    private DatasetService datasetService;

    @Mock
    private DatasetEmbeddingConfigValidator embeddingConfigValidator;

    @InjectMocks
    private DatasetParseConfigServiceImpl service;

    @BeforeAll
    static void initMpTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DatasetParseConfig.class);
    }

    @Test
    @DisplayName("Should_EchoStored_When_ConfigRowExists")
    void Should_EchoStored_When_ConfigRowExists() {
        DatasetParseConfig entity = new DatasetParseConfig();
        ChunkingConfig chunking = new ChunkingConfig();
        chunking.setOverlapTokens(32);
        entity.setChunkingConfig(chunking);
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(entity);

        DatasetParseConfigResponse resp = service.getConfig(1L, 10L);

        assertThat(resp.getChunking().getOverlapTokens()).isEqualTo(32);
    }

    @Test
    @DisplayName("Should_ReturnEmptyAndNotInsert_When_NoConfigRow")
    void Should_ReturnEmptyAndNotInsert_When_NoConfigRow() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        DatasetParseConfigResponse resp = service.getConfig(1L, 10L);

        // 四类为空对象（非 null），且无任何字段值——未配置
        assertThat(resp.getChunking()).isNotNull();
        assertThat(resp.getChunking().getOverlapTokens()).isNull();
        assertThat(resp.getRecall()).isNotNull();
        assertThat(resp.getRecall().getRecallEnabledSources()).containsExactly("bm25", "sparse", "dense");
        assertThat(resp.getRecall().getRerankTopN()).isEqualTo(8);
        assertThat(resp.getRecall().getRecallStrict()).isFalse();
        verify(datasetParseConfigMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Should_FillRecallDefaults_When_OldRowMissesNewFields")
    void Should_FillRecallDefaults_When_OldRowMissesNewFields() {
        RecallConfig storedRecall = new RecallConfig();
        storedRecall.setDenseTopK(5);
        DatasetParseConfig entity = new DatasetParseConfig();
        entity.setRecallConfig(storedRecall);
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(entity);

        DatasetParseConfigResponse resp = service.getConfig(1L, 10L);

        assertThat(resp.getRecall().getDenseTopK()).isEqualTo(5);
        assertThat(resp.getRecall().getRecallEnabledSources()).containsExactly("bm25", "sparse", "dense");
        assertThat(resp.getRecall().getRerankTopN()).isEqualTo(8);
        assertThat(resp.getRecall().getRecallStrict()).isFalse();
    }

    @Test
    @DisplayName("Should_InsertAllFourAndNotFillDefaults_When_NoRow")
    void Should_InsertAllFourAndNotFillDefaults_When_NoRow() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        ChunkingConfig chunking = new ChunkingConfig();
        chunking.setOverlapTokens(16);
        chunking.setMinCandidateChunkTokens(128);
        chunking.setMaxChunkTokens(768);
        chunking.setHardMaxTokens(1400);
        chunking.setStageTwoAlgorithm(" Semantic_Depth_Window ");
        chunking.setProtectedNeighborOverlap(true);
        req.setChunking(chunking);

        service.updateConfig(1L, 10L, req);

        ArgumentCaptor<DatasetParseConfig> captor = ArgumentCaptor.forClass(DatasetParseConfig.class);
        verify(datasetParseConfigMapper).insert(captor.capture());
        DatasetParseConfig saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getDatasetId()).isEqualTo(10L);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getChunkingConfig().getOverlapTokens()).isEqualTo(16);
        assertThat(saved.getChunkingConfig().getMaxChunkTokens()).isEqualTo(768);
        assertThat(saved.getChunkingConfig().getHardMaxTokens()).isEqualTo(1400);
        assertThat(saved.getChunkingConfig().getStageTwoAlgorithm()).isEqualTo("semantic_depth_window");
        assertThat(saved.getChunkingConfig().getProtectedNeighborOverlap()).isEqualTo(true);
        // 未提交字段不补默认
        assertThat(saved.getChunkingConfig().getHeadingBreakLevel()).isNull();
        // 未提交类写空对象（非 null，序列化为 {}）
        assertThat(saved.getEnhancementConfig()).isNotNull();
        assertThat(saved.getEnhancementConfig().getEnableTableEnhancement()).isNull();
        verify(datasetParseConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_NormalizeRecallSources_When_UpdateConfig")
    void Should_NormalizeRecallSources_When_UpdateConfig() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        RecallConfig recall = new RecallConfig();
        recall.setRecallResultLimit(20);
        recall.setBm25TopK(30);
        recall.setSparseTopK(10);
        recall.setSparseScoreThreshold(0.1);
        recall.setDenseTopK(12);
        recall.setDenseScoreThreshold(0.2);
        recall.setRecallEnabledSources(java.util.List.of(" DENSE ", "", "bm25", "dense"));
        recall.setRecallFusionStrategy(" Weighted_Score ");
        recall.setFusionBm25Weight(1.0);
        recall.setFusionSparseWeight(0.8);
        recall.setFusionDenseWeight(1.2);
        recall.setRerankTopN(3);
        recall.setRecallStrict(true);
        req.setRecall(recall);

        DatasetParseConfigResponse resp = service.updateConfig(1L, 10L, req);

        ArgumentCaptor<DatasetParseConfig> captor = ArgumentCaptor.forClass(DatasetParseConfig.class);
        verify(datasetParseConfigMapper).insert(captor.capture());
        assertThat(captor.getValue().getRecallConfig().getRecallEnabledSources()).containsExactly("dense", "bm25");
        assertThat(captor.getValue().getRecallConfig().getRecallFusionStrategy()).isEqualTo("weighted_score");
        assertThat(captor.getValue().getRecallConfig().getBm25TopK()).isEqualTo(30);
        assertThat(captor.getValue().getRecallConfig().getFusionDenseWeight()).isEqualTo(1.2);
        assertThat(resp.getRecall().getRecallEnabledSources()).containsExactly("dense", "bm25");
        assertThat(resp.getRecall().getRecallFusionStrategy()).isEqualTo("weighted_score");
        assertThat(resp.getRecall().getRerankTopN()).isEqualTo(3);
        assertThat(resp.getRecall().getRecallStrict()).isTrue();
    }

    @Test
    @DisplayName("Should_RejectRecallSource_When_UpdateConfigContainsUnknown")
    void Should_RejectRecallSource_When_UpdateConfigContainsUnknown() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        RecallConfig recall = new RecallConfig();
        recall.setRecallEnabledSources(java.util.List.of("bm25", "unknown"));
        req.setRecall(recall);

        assertThatThrownBy(() -> service.updateConfig(1L, 10L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("recall_enabled_sources");

        verify(datasetParseConfigMapper, never()).insert(any());
        verify(datasetParseConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_RejectRerankTopN_When_UpdateConfigNotPositive")
    void Should_RejectRerankTopN_When_UpdateConfigNotPositive() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        RecallConfig recall = new RecallConfig();
        recall.setRerankTopN(0);
        req.setRecall(recall);

        assertThatThrownBy(() -> service.updateConfig(1L, 10L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("rerank_top_n");

        verify(datasetParseConfigMapper, never()).insert(any());
        verify(datasetParseConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_RejectChunkBounds_When_MaxLessThanMin")
    void Should_RejectChunkBounds_When_MaxLessThanMin() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        ChunkingConfig chunking = new ChunkingConfig();
        chunking.setMinCandidateChunkTokens(256);
        chunking.setMaxChunkTokens(128);
        req.setChunking(chunking);

        assertThatThrownBy(() -> service.updateConfig(1L, 10L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("max_chunk_tokens");

        verify(datasetParseConfigMapper, never()).insert(any());
        verify(datasetParseConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_RejectRecallFusionWeight_When_Negative")
    void Should_RejectRecallFusionWeight_When_Negative() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        RecallConfig recall = new RecallConfig();
        recall.setFusionDenseWeight(-0.1);
        req.setRecall(recall);

        assertThatThrownBy(() -> service.updateConfig(1L, 10L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("fusion_dense_weight");

        verify(datasetParseConfigMapper, never()).insert(any());
        verify(datasetParseConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_RejectRecallFusionStrategy_When_Unknown")
    void Should_RejectRecallFusionStrategy_When_Unknown() {
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(null);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        RecallConfig recall = new RecallConfig();
        recall.setRecallFusionStrategy("unknown");
        req.setRecall(recall);

        assertThatThrownBy(() -> service.updateConfig(1L, 10L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("recall_fusion_strategy");

        verify(datasetParseConfigMapper, never()).insert(any());
        verify(datasetParseConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_UpdateExistingRow_When_RowExists")
    void Should_UpdateExistingRow_When_RowExists() {
        DatasetParseConfig existing = new DatasetParseConfig();
        existing.setId(5L);
        existing.setSparseEmbeddingConfigId(11L);
        existing.setDenseEmbeddingConfigId(12L);
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(existing);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        ChunkingConfig chunking = new ChunkingConfig();
        chunking.setOverlapTokens(20);
        req.setChunking(chunking);

        service.updateConfig(1L, 10L, req);

        ArgumentCaptor<DatasetParseConfig> captor = ArgumentCaptor.forClass(DatasetParseConfig.class);
        verify(datasetParseConfigMapper).updateById(captor.capture());
        verify(datasetParseConfigMapper, never()).insert(any());
        DatasetParseConfig updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(5L);
        assertThat(updated.getSparseEmbeddingConfigId()).isEqualTo(11L);
        assertThat(updated.getDenseEmbeddingConfigId()).isEqualTo(12L);
        assertThat(updated.getChunkingConfig().getOverlapTokens()).isEqualTo(20);
        // 修复「最后更新时间不变」：更新只写主键+四类，不显式写时间字段，交 DB ON UPDATE 刷新 updated_at
        assertThat(updated.getUpdatedAt()).isNull();
        assertThat(updated.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("Should_RejectModelRebind_When_ExistingBindingDiffers")
    void Should_RejectModelRebind_When_ExistingBindingDiffers() {
        DatasetParseConfig existing = new DatasetParseConfig();
        existing.setId(5L);
        existing.setSparseEmbeddingConfigId(11L);
        existing.setDenseEmbeddingConfigId(12L);
        given(datasetService.detail(anyLong(), anyLong())).willReturn(null);
        given(datasetParseConfigMapper.selectOne(any())).willReturn(existing);

        UpdateDatasetParseConfigRequest req = new UpdateDatasetParseConfigRequest();
        req.setSparseEmbeddingConfigId(11L);
        req.setDenseEmbeddingConfigId(13L);

        assertThatThrownBy(() -> service.updateConfig(1L, 10L, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("dense_embedding_config_id");

        verify(datasetParseConfigMapper, never()).insert(any());
        verify(datasetParseConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("Should_Throw404AndNotWrite_When_DatasetNotOwned")
    void Should_Throw404AndNotWrite_When_DatasetNotOwned() {
        given(datasetService.detail(anyLong(), anyLong()))
            .willThrow(new BusinessException(404, "数据集不存在或无权访问", 404));

        assertThatThrownBy(() -> service.updateConfig(1L, 10L, new UpdateDatasetParseConfigRequest()))
            .isInstanceOf(BusinessException.class)
            .hasMessage("数据集不存在或无权访问");

        verify(datasetParseConfigMapper, never()).insert(any());
        verify(datasetParseConfigMapper, never()).updateById(any());
    }
}

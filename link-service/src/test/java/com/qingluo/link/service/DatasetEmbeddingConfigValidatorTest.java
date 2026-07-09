package com.qingluo.link.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.qingluo.link.mapper.SystemPresetMapper;
import com.qingluo.link.mapper.UserLLMConfigMapper;
import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DatasetEmbeddingConfigValidatorTest {

    @Mock
    private UserLLMConfigMapper userLLMConfigMapper;
    @Mock
    private SystemPresetMapper systemPresetMapper;

    @InjectMocks
    private DatasetEmbeddingConfigValidator validator;

    @BeforeAll
    static void initTableInfoCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration,
            new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, UserLLMConfig.class);
        TableInfoHelper.initTableInfo(assistant, SystemPreset.class);
    }

    @Test
    @DisplayName("数据集绑定传入 LinkRag 系统兜底 ID 时解析为 SYSTEM 来源")
    void validateBindingPair_acceptsSystemPresetId() {
        SystemPreset sparse = preset(10004L, DatasetEmbeddingConfigValidator.SPARSE_EMBEDDING);
        SystemPreset dense = preset(10005L, DatasetEmbeddingConfigValidator.EMBEDDING);
        given(userLLMConfigMapper.selectOne(any())).willReturn(null);
        given(systemPresetMapper.selectOne(any())).willReturn(sparse, dense);

        DatasetEmbeddingConfigValidator.ResolvedBindingPair result =
            validator.validateAndResolveBindingPair(10000L, 10004L, null, 10005L, null);

        assertThat(result.sparse().source()).isEqualTo(DatasetEmbeddingConfigValidator.SOURCE_SYSTEM);
        assertThat(result.dense().source()).isEqualTo(DatasetEmbeddingConfigValidator.SOURCE_SYSTEM);
    }

    private SystemPreset preset(Long id, String capability) {
        SystemPreset preset = new SystemPreset();
        preset.setId(id);
        preset.setProviderType("linkrag");
        preset.setCapability(capability);
        preset.setIsDefault(true);
        preset.setIsActive(true);
        return preset;
    }
}

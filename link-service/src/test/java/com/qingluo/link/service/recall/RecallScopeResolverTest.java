package com.qingluo.link.service.recall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * datasetIds 校验/展开（acceptance 场景 3/8/9/10/27）。
 */
@ExtendWith(MockitoExtension.class)
class RecallScopeResolverTest {

    @Mock
    private DatasetMapper datasetMapper;

    @InjectMocks
    private RecallScopeResolver resolver;

    /** 单元测试无 MyBatis 扫描，手动初始化 MP TableInfo，使 LambdaQueryWrapper 能解析列。 */
    @BeforeAll
    static void initMpTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Dataset.class);
    }

    @Test
    @DisplayName("Should_ReturnRequestedIds_When_AllOwned")
    void Should_ReturnRequestedIds_When_AllOwned() {
        given(datasetMapper.selectCount(any())).willReturn(2L);

        ResolvedScope scope = resolver.resolve(100L, List.of(1L, 2L));

        assertThat(scope.emptyOwnership()).isFalse();
        assertThat(scope.datasetIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Should_ThrowForbidden_When_SomeDatasetNotOwned")
    void Should_ThrowForbidden_When_SomeDatasetNotOwned() {
        given(datasetMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> resolver.resolve(100L, List.of(1L, 2L)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Should_ExpandToAllOwnedDatasets_When_EmptyList")
    void Should_ExpandToAllOwnedDatasets_When_EmptyList() {
        given(datasetMapper.selectList(any())).willReturn(List.of(dataset(1L), dataset(2L)));

        ResolvedScope scope = resolver.resolve(100L, List.of());

        assertThat(scope.emptyOwnership()).isFalse();
        assertThat(scope.datasetIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Should_ReturnEmptyOwnership_When_EmptyListAndNoDatasets")
    void Should_ReturnEmptyOwnership_When_EmptyListAndNoDatasets() {
        given(datasetMapper.selectList(any())).willReturn(List.of());

        ResolvedScope scope = resolver.resolve(100L, List.of());

        assertThat(scope.emptyOwnership()).isTrue();
        assertThat(scope.datasetIds()).isEmpty();
    }

    private Dataset dataset(Long id) {
        Dataset dataset = new Dataset();
        dataset.setId(id);
        return dataset;
    }
}

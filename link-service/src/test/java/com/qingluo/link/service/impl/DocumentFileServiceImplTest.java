package com.qingluo.link.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.mq.AbstractMQ;
import com.qingluo.link.components.mq.MQSend;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.components.oss.service.PrivateFileResolver;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.mapper.DatasetMapper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.model.dto.entity.Dataset;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.response.DocumentFileDTO;
import com.qingluo.link.service.DocumentFileRuntimeConfigService;
import com.qingluo.link.service.config.DocumentFileProperties;
import com.qingluo.link.service.config.DocumentFileRuntimeConfig;
import com.qingluo.link.service.delete.DocumentDeleteNotifier;
import com.qingluo.link.service.mq.DocumentParseTaskMQ;

import com.qingluo.link.service.impl.document.DocumentFileServiceImpl;
import com.qingluo.link.service.impl.document.DocumentUploadAsyncExecutor;
import com.qingluo.link.service.impl.document.DocumentUploadTempStorage;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class DocumentFileServiceImplTest {

    @Mock
    private DatasetMapper datasetMapper;

    @Mock
    private DocumentOriginalFileMapper documentOriginalFileMapper;

    @Mock
    private DocumentDeleteNotifier deleteNotifier;

    @Mock
    private IOssService ossService;

    @Mock
    private PrivateFileResolver privateFileResolver;

    @Mock
    private ObjectProvider<MQSend> mqSendProvider;

    @Mock
    private DocumentFileProperties properties;

    @Mock
    private DocumentFileRuntimeConfigService documentFileRuntimeConfigService;

    @Mock
    private DocumentUploadAsyncExecutor asyncExecutor;

    @Mock
    private DocumentUploadTempStorage tempStorage;

    @InjectMocks
    private DocumentFileServiceImpl documentFileService;

    /** 单元测试无 MyBatis 启动扫描，手动初始化 MP TableInfo，使同名复用的 LambdaUpdateWrapper 能解析列。 */
    @BeforeAll
    static void initMpTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), DocumentOriginalFile.class);
    }

    @Test
    @DisplayName("Should_CreateParseTaskMq_When_NoArgConstructor")
    void Should_CreateParseTaskMq_When_NoArgConstructor() {
        AbstractMQ mq = new DocumentParseTaskMQ();

        assertEquals("tolink.rag.parse_task", mq.getMQName());
    }

    @Test
    @DisplayName("Should_SoftDeleteFileWithoutOssOrParse_When_DeleteFile")
    void Should_SoftDeleteFileWithoutOssOrParse_When_DeleteFile() {
        given(documentOriginalFileMapper.selectOne(any())).willReturn(buildFile(1L, 100L, 200L));

        documentFileService.delete(100L, 1L);

        // 原文件软删（update），不物理删 DB 行
        verify(documentOriginalFileMapper).update(any(), any());
        verify(documentOriginalFileMapper, never()).deleteById(any(Long.class));
        // 不删 OSS 对象、不清本地缓存
        verify(ossService, never()).deleteFile(any(), any());
        verify(privateFileResolver, never()).evictPrivateFile(any());
        // 提交后通知 Python（占位），载荷含该文件 id + datasetId + userId
        verify(deleteNotifier).notifyAfterDelete(List.of(1L), 200L, 100L);
    }

    @Test
    @DisplayName("Should_Return404AndNotDelete_When_DeleteFileNotOwned")
    void Should_Return404AndNotDelete_When_DeleteFileNotOwned() {
        given(documentOriginalFileMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> documentFileService.delete(100L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("文件不存在或无权访问");

        verify(documentOriginalFileMapper, never()).update(any(), any());
        verify(deleteNotifier, never()).notifyAfterDelete(any(), any(), any());
    }

    @Test
    @DisplayName("S2 数据集不存在或无权 → 不落记录、不物化、不提交异步")
    void upload_rejectsUnownedDataset() throws Exception {
        given(datasetMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> documentFileService.upload(100L, 200L, validFile(), false))
            .isInstanceOf(BusinessException.class);

        verify(documentOriginalFileMapper, never()).insert(any());
        verify(tempStorage, never()).materialize(any());
        verify(asyncExecutor, never()).submit(any());
    }

    @Test
    @DisplayName("S3/S22 空文件 → 同步 400，不物化/不提交/不落记录")
    void upload_rejectsEmptyFile() throws Exception {
        given(datasetMapper.selectOne(any())).willReturn(new Dataset());
        MockMultipartFile empty = new MockMultipartFile("file", "test.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> documentFileService.upload(100L, 200L, empty, false))
            .isInstanceOf(BusinessException.class);

        verify(tempStorage, never()).materialize(any());
        verify(asyncExecutor, never()).submit(any());
        verify(documentOriginalFileMapper, never()).insert(any());
    }

    @Test
    @DisplayName("S3 不支持的后缀 → 同步 400，不物化")
    void upload_rejectsUnsupportedSuffix() throws Exception {
        given(datasetMapper.selectOne(any())).willReturn(new Dataset());
        DocumentFileRuntimeConfig rc = mock(DocumentFileRuntimeConfig.class);
        given(rc.getAllowedSuffixes()).willReturn(Set.of("txt"));
        given(documentFileRuntimeConfigService.getCurrent()).willReturn(rc);
        MockMultipartFile exe = new MockMultipartFile("file", "bad.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> documentFileService.upload(100L, 200L, exe, false))
            .isInstanceOf(BusinessException.class);

        verify(tempStorage, never()).materialize(any());
        verify(asyncExecutor, never()).submit(any());
    }

    @Test
    @DisplayName("S4 校验通过 → 落 uploading 立即返回并提交异步任务")
    void upload_returnsUploadingAndSubmitsAsync() throws Exception {
        givenOwnedDatasetAndTxtAllowed();
        given(documentOriginalFileMapper.selectOne(any())).willReturn(null);
        given(documentOriginalFileMapper.insert(any())).willReturn(1);
        given(tempStorage.materialize(any())).willReturn(Path.of("/tmp/doc-upload-x.tmp"));

        DocumentFileDTO dto = documentFileService.upload(100L, 200L, validFile(), true);

        assertThat(dto.getUploadStatus()).isEqualTo("UPLOADING");
        assertThat(dto.getIsUploadSuccess()).isFalse();
        verify(documentOriginalFileMapper).insert(any());
        verify(asyncExecutor).submit(any());
    }

    @Test
    @DisplayName("S13/S14 同名 failed → 复用旧行重置 uploading，不插新行")
    void upload_reusesFailedRecord() throws Exception {
        givenOwnedDatasetAndTxtAllowed();
        DocumentOriginalFile failed = new DocumentOriginalFile();
        failed.setId(9L);
        failed.setUploadStatus("failed");
        given(documentOriginalFileMapper.selectOne(any())).willReturn(failed);
        given(documentOriginalFileMapper.update(any(), any())).willReturn(1);
        given(tempStorage.materialize(any())).willReturn(Path.of("/tmp/doc-upload-x.tmp"));

        DocumentFileDTO dto = documentFileService.upload(100L, 200L, validFile(), false);

        assertThat(dto.getUploadStatus()).isEqualTo("UPLOADING");
        verify(documentOriginalFileMapper, never()).insert(any());
        verify(documentOriginalFileMapper).update(any(), any());
        verify(asyncExecutor).submit(any());
    }

    @Test
    @DisplayName("S15 同名且为 success/uploading → 400 拦截，不复用、不物化、不提交")
    void upload_rejectsDuplicateNonFailed() throws Exception {
        givenOwnedDatasetAndTxtAllowed();
        DocumentOriginalFile success = new DocumentOriginalFile();
        success.setId(9L);
        success.setUploadStatus("success");
        given(documentOriginalFileMapper.selectOne(any())).willReturn(success);

        assertThatThrownBy(() -> documentFileService.upload(100L, 200L, validFile(), false))
            .isInstanceOf(BusinessException.class);

        verify(documentOriginalFileMapper, never()).insert(any());
        verify(documentOriginalFileMapper, never()).update(any(), any());
        verify(tempStorage, never()).materialize(any());
        verify(asyncExecutor, never()).submit(any());
    }

    private MockMultipartFile validFile() {
        return new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());
    }

    private void givenOwnedDatasetAndTxtAllowed() {
        given(datasetMapper.selectOne(any())).willReturn(new Dataset());
        DocumentFileRuntimeConfig rc = mock(DocumentFileRuntimeConfig.class);
        given(rc.getAllowedSuffixes()).willReturn(Set.of("txt"));
        given(rc.getMaxSizeBytes()).willReturn(10L * 1024 * 1024);
        given(documentFileRuntimeConfigService.getCurrent()).willReturn(rc);
    }

    private DocumentOriginalFile buildFile(Long fileId, Long userId, Long datasetId) {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setId(fileId);
        file.setUserId(userId);
        file.setDatasetId(datasetId);
        return file;
    }
}

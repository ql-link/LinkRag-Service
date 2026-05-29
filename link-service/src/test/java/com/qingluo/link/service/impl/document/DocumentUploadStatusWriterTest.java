package com.qingluo.link.service.impl.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qingluo.link.mapper.DocumentOriginalFileMapper;
import com.qingluo.link.mapper.DocumentParseFileMapper;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.service.DocumentParseTaskService;
import com.qingluo.link.service.config.DocumentFileProperties;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 终态守卫回写测试，覆盖 S5/S6/S7（成功回写与解析投递时机）、S16（孤儿对象不投递）、S8/S11（置 failed）。
 */
@ExtendWith(MockitoExtension.class)
class DocumentUploadStatusWriterTest {

    @Mock
    private DocumentOriginalFileMapper documentOriginalFileMapper;
    @Mock
    private DocumentParseFileMapper documentParseFileMapper;
    @Mock
    private DocumentParseTaskService documentParseTaskService;
    @Mock
    private DocumentFileProperties properties;

    @InjectMocks
    private DocumentUploadStatusWriter statusWriter;

    /** 单元测试无 MyBatis 启动扫描，手动初始化 MP TableInfo，使 LambdaUpdateWrapper 能解析列。 */
    @BeforeAll
    static void initMpTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), DocumentOriginalFile.class);
    }

    @Test
    @DisplayName("S5/S6 守卫更新命中且 parseImmediately=true 时上传成功后投递解析")
    void markUploadSuccess_hit_parseImmediately_submitsParse() {
        given(properties.getInternalBaseUrl()).willReturn("http://localhost:8080");
        given(documentOriginalFileMapper.update(any(), any())).willReturn(1);
        DocumentOriginalFile record = record(7L);
        given(documentOriginalFileMapper.selectById(7L)).willReturn(record);
        given(documentParseFileMapper.selectOne(any())).willReturn(null);

        statusWriter.markUploadSuccess(7L, "u/d/test.txt", true, 100L);

        verify(documentParseFileMapper).insert(any());
        verify(documentParseTaskService).submitAutoParseAfterUpload(eq(100L), eq(record));
    }

    @Test
    @DisplayName("S7 parseImmediately=false 时不投递解析")
    void markUploadSuccess_hit_noParse_whenNotImmediate() {
        given(properties.getInternalBaseUrl()).willReturn("http://localhost:8080");
        given(documentOriginalFileMapper.update(any(), any())).willReturn(1);
        given(documentOriginalFileMapper.selectById(7L)).willReturn(record(7L));
        given(documentParseFileMapper.selectOne(any())).willReturn(null);

        statusWriter.markUploadSuccess(7L, "u/d/test.txt", false, 100L);

        verify(documentParseTaskService, never()).submitAutoParseAfterUpload(any(), any());
    }

    @Test
    @DisplayName("S16 守卫更新命中 0 行（已被超时置 failed）→ 孤儿，不投递解析、不建聚合")
    void markUploadSuccess_missGuard_isOrphan_noParse() {
        given(properties.getInternalBaseUrl()).willReturn("http://localhost:8080");
        given(documentOriginalFileMapper.update(any(), any())).willReturn(0);

        statusWriter.markUploadSuccess(7L, "u/d/test.txt", true, 100L);

        verify(documentOriginalFileMapper, never()).selectById(any());
        verify(documentParseFileMapper, never()).insert(any());
        verify(documentParseTaskService, never()).submitAutoParseAfterUpload(any(), any());
    }

    @Test
    @DisplayName("S8/S11 markUploadFailed 守卫更新置 failed")
    void markUploadFailed_guardedUpdate() {
        statusWriter.markUploadFailed(7L, "上传超时，请重试");
        verify(documentOriginalFileMapper).update(any(), any());
    }

    private DocumentOriginalFile record(Long id) {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setId(id);
        file.setDatasetId(200L);
        file.setUserId(100L);
        file.setOriginalFilename("test.txt");
        return file;
    }
}

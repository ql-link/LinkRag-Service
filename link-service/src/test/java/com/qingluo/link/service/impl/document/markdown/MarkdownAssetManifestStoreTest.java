package com.qingluo.link.service.impl.document.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.dto.entity.DocumentOriginalFile;
import com.qingluo.link.model.dto.response.MarkdownAssetSummaryDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkdownAssetManifestStoreTest {

    @Mock
    private IOssService ossService;

    @TempDir
    Path tempDir;

    @Test
    void readsAndValidatesCommittedManifest() throws Exception {
        MarkdownAssetManifestStore store = new MarkdownAssetManifestStore(ossService);
        DocumentOriginalFile file = file();
        MarkdownAssetManifest manifest = manifest(file);
        Path materialized = store.materialize(tempDir, manifest);
        given(ossService.downloadFile(eq(OssSavePlaceEnum.RAW), anyString(), anyString()))
            .willAnswer(invocation -> {
                Files.write(Path.of((String) invocation.getArgument(2)), Files.readAllBytes(materialized));
                return true;
            });

        MarkdownAssetManifest loaded = store.readRequired(file);

        assertThat(loaded.getFileId()).isEqualTo(3L);
        assertThat(loaded.getSummary().getOutcome()).isEqualTo("READY");
    }

    @Test
    void missingOrMismatchedManifestFailsClosed() {
        MarkdownAssetManifestStore store = new MarkdownAssetManifestStore(ossService);
        given(ossService.downloadFile(eq(OssSavePlaceEnum.RAW), anyString(), anyString()))
            .willReturn(false);

        assertThatThrownBy(() -> store.readRequired(file()))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(50004);
    }

    private DocumentOriginalFile file() {
        DocumentOriginalFile file = new DocumentOriginalFile();
        file.setId(3L);
        file.setUserId(1L);
        file.setDatasetId(2L);
        file.setObjectKey("markdown-assets/v1/user-1/dataset-2/file-3/source/normalized.md");
        return file;
    }

    private MarkdownAssetManifest manifest(DocumentOriginalFile file) {
        MarkdownAssetManifest manifest = new MarkdownAssetManifest();
        manifest.setFileId(file.getId());
        manifest.setUserId(file.getUserId());
        manifest.setDatasetId(file.getDatasetId());
        manifest.setSummary(new MarkdownAssetSummaryDTO());
        MarkdownAssetManifest.SourceEntry source = new MarkdownAssetManifest.SourceEntry();
        source.setNormalizedObjectKey(file.getObjectKey());
        manifest.setSource(source);
        return manifest;
    }
}

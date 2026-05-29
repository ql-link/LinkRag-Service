package com.qingluo.link.service.impl.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.qingluo.link.service.config.DocumentUploadAsyncProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 临时文件物化/清理测试，覆盖 S4（物化）、S12（启动清理残留）。
 */
class DocumentUploadTempStorageTest {

    private DocumentUploadTempStorage newStorage(Path dir) {
        DocumentUploadAsyncProperties props = new DocumentUploadAsyncProperties();
        props.setTempDir(dir.toString());
        return new DocumentUploadTempStorage(props);
    }

    @Test
    @DisplayName("S4 物化把 MultipartFile 落为本地临时文件，内容一致")
    void materialize_writesLocalTempFile(@TempDir Path dir) throws Exception {
        DocumentUploadTempStorage storage = newStorage(dir);
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "hello-async".getBytes(StandardCharsets.UTF_8));

        Path temp = storage.materialize(file);

        assertThat(Files.exists(temp)).isTrue();
        assertThat(temp.startsWith(dir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.readString(temp)).isEqualTo("hello-async");

        storage.delete(temp);
        assertThat(Files.exists(temp)).isFalse();
    }

    @Test
    @DisplayName("S12 启动清理删除临时目录残留文件")
    void cleanupResidualTempFiles_deletesLeftovers(@TempDir Path dir) throws Exception {
        Path leftover1 = Files.createFile(dir.resolve("a.tmp"));
        Path leftover2 = Files.createFile(dir.resolve("b.tmp"));
        DocumentUploadTempStorage storage = newStorage(dir);

        storage.cleanupResidualTempFiles();

        assertThat(Files.exists(leftover1)).isFalse();
        assertThat(Files.exists(leftover2)).isFalse();
    }
}

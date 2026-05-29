package com.qingluo.link.service.impl.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 异步上传编排测试，覆盖 S5（成功回写）、S8（OSS 失败置 failed）、S9（池满拒绝置 failed）、S16（孤儿仍清临时文件）。
 */
@ExtendWith(MockitoExtension.class)
class DocumentUploadAsyncExecutorTest {

    @Mock
    private IOssService ossService;
    @Mock
    private DocumentUploadStatusWriter statusWriter;
    @Mock
    private DocumentUploadTempStorage tempStorage;
    @Mock
    private Executor documentUploadExecutor;

    @InjectMocks
    private DocumentUploadAsyncExecutor asyncExecutor;

    private DocumentUploadAsyncExecutor.UploadTask task() {
        return new DocumentUploadAsyncExecutor.UploadTask(
            7L, Path.of("/tmp/doc-upload-x.tmp"), "u/d/test.txt", "text/plain", true, 100L);
    }

    @Test
    @DisplayName("S5 OSS 上传成功 → 回写 success 并清理临时文件")
    void runUpload_success() {
        given(ossService.upload2PreviewUrl(eq(OssSavePlaceEnum.PRIVATE), any(File.class), eq("text/plain"), eq("u/d/test.txt")))
            .willReturn("u/d/test.txt");

        asyncExecutor.runUpload(task());

        verify(statusWriter).markUploadSuccess(7L, "u/d/test.txt", true, 100L);
        verify(tempStorage).delete(any(Path.class));
    }

    @Test
    @DisplayName("S8 OSS 返回空 → 置 failed 并清理临时文件")
    void runUpload_ossBlank_marksFailed() {
        given(ossService.upload2PreviewUrl(any(), any(File.class), any(), any())).willReturn(null);

        asyncExecutor.runUpload(task());

        verify(statusWriter).markUploadFailed(7L, "文件上传失败，请稍后重试");
        verify(statusWriter, never()).markUploadSuccess(any(), any(), any(Boolean.class), any());
        verify(tempStorage).delete(any(Path.class));
    }

    @Test
    @DisplayName("S16 OSS 成功但回写抛异常 → 视为孤儿，不外抛且仍清理临时文件")
    void runUpload_writebackThrows_isOrphan_stillCleans() {
        given(ossService.upload2PreviewUrl(any(), any(File.class), any(), any())).willReturn("u/d/test.txt");
        doThrow(new RuntimeException("db down")).when(statusWriter).markUploadSuccess(any(), any(), any(Boolean.class), any());

        asyncExecutor.runUpload(task());

        verify(tempStorage).delete(any(Path.class));
    }

    @Test
    @DisplayName("S9 线程池拒绝 → 置 failed（服务繁忙）并清理临时文件，不退回同步")
    void submit_rejected_marksFailed() {
        doThrow(new RejectedExecutionException("pool full")).when(documentUploadExecutor).execute(any());

        asyncExecutor.submit(task());

        verify(statusWriter).markUploadFailed(7L, "服务繁忙，请稍后重试");
        verify(tempStorage).delete(any(Path.class));
        verify(ossService, never()).upload2PreviewUrl(any(), any(File.class), any(), any());
    }

    @Test
    @DisplayName("提交成功时在池线程执行 OSS 上传")
    void submit_runsTaskOnPool() {
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(documentUploadExecutor).execute(any());
        given(ossService.upload2PreviewUrl(any(), any(File.class), any(), any())).willReturn("u/d/test.txt");

        asyncExecutor.submit(task());

        verify(ossService).upload2PreviewUrl(eq(OssSavePlaceEnum.PRIVATE), any(File.class), eq("text/plain"), eq("u/d/test.txt"));
        verify(statusWriter).markUploadSuccess(7L, "u/d/test.txt", true, 100L);
    }
}

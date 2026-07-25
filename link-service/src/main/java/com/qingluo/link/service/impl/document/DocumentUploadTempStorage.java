package com.qingluo.link.service.impl.document;

import com.qingluo.link.service.config.DocumentUploadAsyncProperties;
import com.qingluo.link.service.impl.document.markdown.MarkdownAssetFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import com.qingluo.link.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传临时文件的物化与清理。
 *
 * <p>请求期 {@link MultipartFile} 的底层临时文件随请求结束被容器回收，异步线程无法再读；
 * 故在请求线程内把内容“据为己有”到本组件的专用临时目录（与容器 multipart 临时目录同卷时
 * {@code transferTo} 走 rename，≈免费）。终态后清理；进程异常退出残留由启动清理兜底。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadTempStorage implements ApplicationRunner {

    private final DocumentUploadAsyncProperties properties;

    /**
     * 物化：把请求期 MultipartFile 落为自管的本地临时文件并取得所有权。流只读一次。
     */
    public Path materialize(MultipartFile file) throws IOException {
        Path dir = resolveDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(UUID.randomUUID() + ".tmp");
        try {
            file.transferTo(target.toFile());
        } catch (IllegalStateException e) {
            throw new IOException("transfer multipart to temp file failed", e);
        }
        return target;
    }

    public ManagedBundle materializeBundle(
            MultipartFile file,
            List<MultipartFile> assets,
            List<String> assetRelativePaths) throws IOException {
        if (assets == null) {
            assets = List.of();
        }
        if (assetRelativePaths == null) {
            assetRelativePaths = List.of();
        }
        if (assets.size() != assetRelativePaths.size()) {
            throw new BusinessException(400, "配套图片数量与路径数量不一致", 400);
        }
        Path directory = resolveDir().resolve("bundle-" + UUID.randomUUID());
        Files.createDirectories(directory);
        try {
            Path source = directory.resolve("source.tmp");
            file.transferTo(source.toFile());
            List<MarkdownAssetFile> managedAssets = new ArrayList<>(assets.size());
            for (int i = 0; i < assets.size(); i++) {
                MultipartFile asset = assets.get(i);
                Path target = directory.resolve("asset-" + UUID.randomUUID() + ".tmp");
                asset.transferTo(target.toFile());
                managedAssets.add(new MarkdownAssetFile(
                    assetRelativePaths.get(i),
                    asset.getOriginalFilename(),
                    target,
                    asset.getContentType(),
                    asset.getSize()));
            }
            return new ManagedBundle(directory, source, managedAssets);
        } catch (IllegalStateException e) {
            delete(directory);
            throw new IOException("transfer multipart bundle to temp files failed", e);
        } catch (IOException | RuntimeException e) {
            delete(directory);
            throw e;
        }
    }

    /**
     * 终态后清理临时文件，吞 IO 异常仅日志（清理失败不影响主流程）。
     */
    public void delete(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            if (Files.isDirectory(tempFile)) {
                try (Stream<Path> paths = Files.walk(tempFile)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Delete upload temp path failed, path={}", path, e);
                        }
                    });
                }
                return;
            }
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("Delete upload temp file failed, path={}", tempFile, e);
        }
    }

    public void deleteAll(ManagedBundle bundle) {
        if (bundle != null) {
            delete(bundle.directory());
        }
    }

    /**
     * 启动清理：删除上次进程异常退出残留在专用临时目录的文件（仅该目录、平铺一层）。
     */
    public void cleanupResidualTempFiles() {
        Path dir = resolveDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(this::delete);
        } catch (IOException e) {
            log.warn("Cleanup residual upload temp files failed, dir={}", dir, e);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        cleanupResidualTempFiles();
    }

    private Path resolveDir() {
        return Path.of(properties.getTempDir()).toAbsolutePath().normalize();
    }

    public record ManagedBundle(
        Path directory,
        Path sourceFile,
        List<MarkdownAssetFile> assets
    ) {
        public ManagedBundle {
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }
}

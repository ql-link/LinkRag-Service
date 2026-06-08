package com.qingluo.link.service.impl.blog;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.BlogAssetType;
import com.qingluo.link.service.BlogContentStorageService;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BlogContentStorageServiceImpl implements BlogContentStorageService {

    private static final Set<String> MARKDOWN_SUFFIXES = Set.of("md", "markdown");
    private static final Set<String> IMAGE_SUFFIXES = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final IOssService ossService;

    @Override
    public String uploadMarkdown(Long postId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Markdown正文不能为空");
        }
        String suffix = suffixOf(file.getOriginalFilename());
        if (!MARKDOWN_SUFFIXES.contains(suffix)) {
            throw badRequest("正文文件仅支持md或markdown格式");
        }

        Path temp = null;
        try {
            temp = Files.createTempFile("tolink-blog-md-", ".tmp");
            file.transferTo(temp);
            assertReadableMarkdown(temp);
            String objectKey = "blog/" + postId + "/content/" + uuid() + ".md";
            String uploaded = ossService.upload2PreviewUrl(
                OssSavePlaceEnum.PRIVATE, temp.toFile(), "text/markdown", objectKey);
            if (!StringUtils.hasText(uploaded)) {
                throw new BusinessException(50002, "Markdown正文上传失败", 500);
            }
            return objectKey;
        } catch (BusinessException e) {
            throw e;
        } catch (CharacterCodingException e) {
            throw badRequest("Markdown正文必须是有效的UTF-8文本");
        } catch (IOException e) {
            throw new BusinessException(50002, "Markdown正文处理失败", 500);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public String readMarkdown(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(50003, "Markdown正文对象Key为空", 500);
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("tolink-blog-read-", ".md");
            boolean downloaded = ossService.downloadFile(OssSavePlaceEnum.PRIVATE, objectKey, temp.toString());
            if (!downloaded) {
                throw new BusinessException(50003, "读取Markdown正文失败", 500);
            }
            return Files.readString(temp, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(50003, "读取Markdown正文失败", 500);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public boolean existsMarkdown(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("tolink-blog-exists-", ".md");
            return ossService.downloadFile(OssSavePlaceEnum.PRIVATE, objectKey, temp.toString());
        } catch (IOException e) {
            return false;
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public StoredObject uploadImage(Long postId, BlogAssetType assetType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("图片文件不能为空");
        }
        String suffix = suffixOf(file.getOriginalFilename());
        if (!IMAGE_SUFFIXES.contains(suffix)) {
            throw badRequest("图片格式不支持");
        }
        assertAllowedImageContentType(suffix, file.getContentType());

        String dir = assetType == BlogAssetType.COVER ? "cover" : "content";
        String objectKey = "blog/" + postId + "/" + dir + "/" + uuid() + "." + suffix;
        String publicUrl = ossService.upload2PreviewUrl(OssSavePlaceEnum.PUBLIC, file, objectKey);
        if (!StringUtils.hasText(publicUrl)) {
            throw new BusinessException(50002, "图片上传失败", 500);
        }
        return new StoredObject(objectKey, publicUrl);
    }

    private void assertReadableMarkdown(Path temp) throws IOException {
        boolean hasText = false;
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader reader = new BufferedReader(
            Channels.newReader(Files.newByteChannel(temp), decoder, -1))) {
            char[] buffer = new char[4096];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                for (int i = 0; i < len; i++) {
                    if (!Character.isWhitespace(buffer[i])) {
                        hasText = true;
                    }
                }
            }
        }
        if (!hasText) {
            throw badRequest("Markdown正文不能为空");
        }
    }

    private void assertAllowedImageContentType(String suffix, String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw badRequest("图片MIME类型不能为空");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        boolean allowed = switch (suffix) {
            case "jpg", "jpeg" -> "image/jpeg".equals(normalized);
            case "png" -> "image/png".equals(normalized);
            case "gif" -> "image/gif".equals(normalized);
            case "webp" -> "image/webp".equals(normalized);
            default -> false;
        };
        if (!allowed) {
            throw badRequest("图片MIME类型不支持");
        }
    }

    private String suffixOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(40001, message, 400);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}

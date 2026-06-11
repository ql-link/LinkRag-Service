package com.qingluo.link.service.impl.blog;

import com.qingluo.link.components.oss.enums.OssSavePlaceEnum;
import com.qingluo.link.components.oss.service.IOssService;
import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.BlogAssetType;
import com.qingluo.link.service.BlogContentStorageService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BlogContentStorageServiceImpl implements BlogContentStorageService {

    private static final Logger log = LoggerFactory.getLogger(BlogContentStorageServiceImpl.class);

    private static final Set<String> MARKDOWN_SUFFIXES = Set.of("md", "markdown");
    private static final Set<String> IMAGE_SUFFIXES = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Pattern MARKDOWN_IMAGE_PATTERN =
        Pattern.compile("!\\[([^\\]]*)]\\((<[^>]+>|[^\\s)]+)(\\s+\"[^\"]*\")?\\)");
    private static final long MAX_AUTO_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final long FAILED_REMOTE_CACHE_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final int MAX_REDIRECTS = 3;

    private final IOssService ossService;
    private final Map<String, Long> failedRemoteImageCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @Override
    public ProcessedMarkdown importMarkdown(Long postId, MultipartFile file, Set<String> knownPublicUrls) {
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
            String markdown = readStrictUtf8(temp);
            return processAndStoreMarkdown(postId, markdown, knownPublicUrls);
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
    public ProcessedMarkdown saveMarkdown(Long postId, String markdown, Set<String> knownPublicUrls) {
        return processAndStoreMarkdown(postId, markdown, knownPublicUrls);
    }

    @Override
    public String readMarkdown(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(50003, "Markdown正文对象Key为空", 500);
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("tolink-blog-read-", ".md");
            boolean downloaded = ossService.downloadFile(OssSavePlaceEnum.PUBLIC, objectKey, temp.toString());
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
            return ossService.downloadFile(OssSavePlaceEnum.PUBLIC, objectKey, temp.toString());
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

        String dir = assetType == BlogAssetType.COVER ? "cover" : "images";
        String objectKey = "blog/" + postId + "/" + dir + "/" + uuid() + "." + suffix;
        String publicUrl = ossService.upload2PreviewUrl(OssSavePlaceEnum.PUBLIC, file, objectKey);
        if (!StringUtils.hasText(publicUrl)) {
            throw new BusinessException(50002, "图片上传失败", 500);
        }
        return new StoredObject(objectKey, publicUrl);
    }

    private ProcessedMarkdown processAndStoreMarkdown(Long postId, String markdown, Set<String> knownPublicUrls) {
        assertMarkdownText(markdown);
        List<StoredMarkdownImage> images = new ArrayList<>();
        String rewritten = rewriteMarkdownImages(postId, markdown, knownPublicUrls, images);
        Path temp = null;
        try {
            temp = Files.createTempFile("tolink-blog-md-store-", ".md");
            Files.writeString(temp, rewritten, StandardCharsets.UTF_8);
            String objectKey = "blog/" + postId + "/content/" + uuid() + ".md";
            String uploaded = ossService.upload2PreviewUrl(
                OssSavePlaceEnum.PUBLIC, temp.toFile(), "text/markdown", objectKey);
            if (!StringUtils.hasText(uploaded)) {
                throw new BusinessException(50002, "Markdown正文上传失败", 500);
            }
            return new ProcessedMarkdown(objectKey, rewritten, images);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(50002, "Markdown正文处理失败", 500);
        } finally {
            deleteQuietly(temp);
        }
    }

    private String rewriteMarkdownImages(
            Long postId, String markdown, Set<String> knownPublicUrls, List<StoredMarkdownImage> images) {
        Set<String> knownUrls = knownPublicUrls == null ? Set.of() : new HashSet<>(knownPublicUrls);
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            String alt = matcher.group(1);
            String source = normalizeMarkdownImageSource(matcher.group(2));
            String title = matcher.group(3) == null ? "" : matcher.group(3);
            String replacementUrl = resolveMarkdownImage(postId, source, knownUrls, images);
            String replacement = "![" + alt + "](" + replacementUrl + title + ")";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private String resolveMarkdownImage(
            Long postId, String source, Set<String> knownPublicUrls, List<StoredMarkdownImage> images) {
        if (!StringUtils.hasText(source)) {
            throw badRequest("Markdown图片地址不能为空");
        }
        if (knownPublicUrls.contains(source)) {
            return source;
        }
        if (source.startsWith("data:image/")) {
            StoredMarkdownImage stored = uploadDataUriImage(postId, source);
            images.add(stored);
            return stored.publicUrl();
        }

        URI uri;
        try {
            uri = URI.create(source);
        } catch (IllegalArgumentException e) {
            throw badRequest("Markdown图片地址不合法: " + source);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw badRequest("Markdown图片仅支持http/https或data URI: " + source);
        }
        StoredMarkdownImage stored = uploadRemoteImage(postId, uri);
        if (stored == null) {
            return source;
        }
        images.add(stored);
        return stored.publicUrl();
    }

    private StoredMarkdownImage uploadRemoteImage(Long postId, URI uri) {
        String source = uri.toString();
        if (isRecentFailedRemote(source)) {
            return null;
        }
        try {
            RemoteImage remote = fetchRemoteImage(uri, 0);
            StoredMarkdownImage stored = uploadMarkdownImageBytes(
                postId, remote.bytes(), remote.suffix(), remote.contentType(), originalFilename(uri, remote.suffix()));
            failedRemoteImageCache.remove(source);
            return stored;
        } catch (Exception e) {
            failedRemoteImageCache.put(source, System.currentTimeMillis());
            log.warn("Skip blog markdown remote image, source={}, reason={}", source, e.getMessage());
            return null;
        }
    }

    private RemoteImage fetchRemoteImage(URI uri, int redirects) throws Exception {
        assertAllowedRemoteUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                if (redirects >= MAX_REDIRECTS) {
                    throw new IOException("remote image redirects exceeded");
                }
                String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IOException("remote image redirect missing Location"));
                return fetchRemoteImage(uri.resolve(location), redirects + 1);
            }
            if (status < 200 || status >= 300) {
                throw new IOException("remote image status " + status);
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_AUTO_IMAGE_BYTES) {
                throw new IOException("remote image exceeds 10MB");
            }
            byte[] bytes = readLimited(body, MAX_AUTO_IMAGE_BYTES);
            String contentType = normalizeContentType(response.headers().firstValue("Content-Type").orElse(null));
            String suffix = suffixFromContentType(contentType);
            if (!StringUtils.hasText(suffix)) {
                suffix = detectImageSuffix(bytes);
            }
            if (!StringUtils.hasText(suffix)) {
                suffix = suffixOf(uri.getPath());
            }
            if (!StringUtils.hasText(contentType) || "application/octet-stream".equals(contentType)) {
                contentType = contentTypeFromSuffix(suffix);
            }
            if (!IMAGE_SUFFIXES.contains(suffix)) {
                throw new IOException("remote image type is unsupported");
            }
            assertAllowedImageContentType(suffix, contentType);
            return new RemoteImage(bytes, suffix, contentType);
        }
    }

    private void assertAllowedRemoteUri(URI uri) throws Exception {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IOException("remote image scheme is unsupported");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host) || "localhost".equalsIgnoreCase(host)) {
            throw new IOException("remote image host is not allowed");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IOException("remote image host is private or local");
            }
        }
    }

    private byte[] readLimited(InputStream inputStream, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        try (var output = new java.io.ByteArrayOutputStream()) {
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("remote image exceeds 10MB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private StoredMarkdownImage uploadDataUriImage(Long postId, String source) {
        int comma = source.indexOf(',');
        if (comma < 0) {
            throw badRequest("Markdown图片data URI不合法");
        }
        String metadata = source.substring(0, comma);
        String lowerMetadata = metadata.toLowerCase(Locale.ROOT);
        int base64Index = lowerMetadata.indexOf(";base64");
        if (base64Index < 0) {
            throw badRequest("Markdown图片data URI仅支持base64");
        }
        String contentType = normalizeContentType(metadata.substring("data:".length(), base64Index));
        String suffix = suffixFromContentType(contentType);
        try {
            byte[] bytes = Base64.getDecoder().decode(source.substring(comma + 1));
            if (bytes.length > MAX_AUTO_IMAGE_BYTES) {
                throw badRequest("Markdown图片不能超过10MB");
            }
            return uploadMarkdownImageBytes(postId, bytes, suffix, contentType, "image." + suffix);
        } catch (IllegalArgumentException e) {
            throw badRequest("Markdown图片data URI不合法");
        }
    }

    private StoredMarkdownImage uploadMarkdownImageBytes(
            Long postId, byte[] bytes, String suffix, String contentType, String originalFilename) {
        if (!IMAGE_SUFFIXES.contains(suffix)) {
            throw badRequest("Markdown图片格式不支持: " + originalFilename);
        }
        assertAllowedImageContentType(suffix, contentType);
        Path temp = null;
        try {
            temp = Files.createTempFile("tolink-blog-image-", "." + suffix);
            Files.write(temp, bytes);
            String objectKey = "blog/" + postId + "/images/" + uuid() + "." + suffix;
            String publicUrl = ossService.upload2PreviewUrl(OssSavePlaceEnum.PUBLIC, temp.toFile(), contentType, objectKey);
            if (!StringUtils.hasText(publicUrl)) {
                throw new BusinessException(50002, "Markdown图片上传失败", 500);
            }
            return new StoredMarkdownImage(objectKey, publicUrl, originalFilename, contentType, bytes.length);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(50002, "Markdown图片处理失败", 500);
        } finally {
            deleteQuietly(temp);
        }
    }

    private String normalizeMarkdownImageSource(String source) {
        if (source != null && source.startsWith("<") && source.endsWith(">") && source.length() > 2) {
            return source.substring(1, source.length() - 1);
        }
        return source;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String normalized = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private String suffixFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

    private String contentTypeFromSuffix(String suffix) {
        return switch (suffix) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "";
        };
    }

    private String detectImageSuffix(byte[] bytes) {
        if (bytes.length >= 4
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47) {
            return "png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "jpg";
        }
        if (bytes.length >= 6) {
            String header = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header) || "GIF89a".equals(header)) {
                return "gif";
            }
        }
        if (bytes.length >= 12) {
            String riff = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
            String webp = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return "webp";
            }
        }
        return "";
    }

    private String originalFilename(URI uri, String suffix) {
        String path = uri.getPath();
        if (StringUtils.hasText(path)) {
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            if (StringUtils.hasText(name) && name.contains(".")) {
                return name;
            }
        }
        return "image." + suffix;
    }

    private void assertAllowedImageContentType(String suffix, String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw badRequest("图片MIME类型不能为空");
        }
        String normalized = normalizeContentType(contentType);
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

    private String readStrictUtf8(Path temp) throws IOException {
        byte[] bytes = Files.readAllBytes(temp);
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    private void assertMarkdownText(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            throw badRequest("Markdown正文不能为空");
        }
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(markdown))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.hasText(line)) {
                    return;
                }
            }
        } catch (IOException ignored) {
        }
        throw badRequest("Markdown正文不能为空");
    }

    private boolean isRecentFailedRemote(String source) {
        Long failedAt = failedRemoteImageCache.get(source);
        if (failedAt == null) {
            return false;
        }
        if (System.currentTimeMillis() - failedAt <= FAILED_REMOTE_CACHE_MILLIS) {
            return true;
        }
        failedRemoteImageCache.remove(source);
        return false;
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

    private record RemoteImage(byte[] bytes, String suffix, String contentType) {
    }
}

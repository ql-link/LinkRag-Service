package com.qingluo.link.model.dto.response;

import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识文件导入能力与安全限制")
public class DocumentFileCapabilitiesDTO {

    @Schema(description = "Markdown 配套图片资源包功能是否启用", example = "true")
    private boolean featureEnabled;
    @Schema(description = "文档上传能力")
    private DocumentCapability document = new DocumentCapability();
    @Schema(description = "Markdown 图片上传能力")
    private ImageCapability image = new ImageCapability();
    @Schema(description = "浏览器 ZIP 预检限制")
    private ZipCapability zip = new ZipCapability();
    @Schema(description = "图片路径匹配模式：FULL_PATH 完整相对路径，SHALLOW_BASENAME 一级文件名")
    private List<String> matchModes = new ArrayList<>();

    @Data
    @Schema(description = "文档上传限制")
    public static class DocumentCapability {
        @Schema(description = "允许的文档扩展名")
        private List<String> allowedSuffixes = new ArrayList<>();
        @Schema(description = "单文档最大字节数", example = "52428800")
        private long maxSizeBytes;
    }

    @Data
    @Schema(description = "Markdown 配套图片限制")
    public static class ImageCapability {
        @Schema(description = "允许的图片扩展名")
        private List<String> extensions = new ArrayList<>();
        @Schema(description = "允许的图片 MIME 类型")
        private List<String> mimeTypes = new ArrayList<>();
        @Schema(description = "单张图片最大字节数", example = "20971520")
        private long maxAssetBytes;
        @Schema(description = "单资源包最大图片数量", example = "200")
        private int maxAssetCount;
        @Schema(description = "单资源包最大目录清单数量", example = "5000")
        private int maxInventoryCount;
        @Schema(description = "文档与图片总字节上限", example = "83886080")
        private long maxBundleBytes;
        @Schema(description = "图片相对路径最大长度", example = "512")
        private int maxPathLength;
        @Schema(description = "文档相对路径最大长度", example = "255")
        private int maxDocumentPathLength;
    }

    @Data
    @Schema(description = "ZIP 数据集包浏览器预检限制")
    public static class ZipCapability {
        @Schema(description = "ZIP 压缩文件最大字节数", example = "104857600")
        private long maxCompressedBytes;
        @Schema(description = "ZIP 最大条目数", example = "5000")
        private int maxEntries;
        @Schema(description = "ZIP 最大展开字节数", example = "524288000")
        private long maxExpandedBytes;
        @Schema(description = "单条目最大压缩比", example = "100")
        private int maxRatio;
        @Schema(description = "ZIP 最大目录深度", example = "20")
        private int maxDepth;
    }
}

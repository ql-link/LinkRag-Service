package com.qingluo.link.model.dto.response;

import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "单个 Markdown 图片引用的匹配结果")
public class MarkdownAssetIssueDTO {

    @Schema(description = "来源语法：MARKDOWN、MARKDOWN_REFERENCE、HTML、OBSIDIAN")
    private String syntax;
    @Schema(description = "文档中的原始图片目标")
    private String originalTarget;
    @Schema(description = "解析后的规范目标路径或首选候选")
    private String normalizedTarget;
    @Schema(description = "匹配结果：MATCHED、MISSING、AMBIGUOUS、UNSUPPORTED")
    private String resolution;
    @Schema(description = "问题类型，无问题时为空")
    private String issueKind;
    @Schema(description = "命中图片的原始文件名")
    private String originalFilename;
    @Schema(description = "按内容摘要生成的安全存储文件名")
    private String storedFilename;
    @Schema(description = "改写后的 RAW 逻辑 URI，不含桶名与凭据")
    private String logicalUri;
    @Schema(description = "有限的字面值与 percent-decode 候选")
    private List<String> basenameCandidates = new ArrayList<>();
    @Schema(description = "候选中实际存在的文件名或路径")
    private List<String> candidateFilenames = new ArrayList<>();
}

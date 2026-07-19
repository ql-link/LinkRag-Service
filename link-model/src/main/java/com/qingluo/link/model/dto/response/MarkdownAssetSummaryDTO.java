package com.qingluo.link.model.dto.response;

import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Markdown 本地图片权威匹配汇总")
public class MarkdownAssetSummaryDTO {

    @Schema(description = "匹配模式：FULL_PATH、SHALLOW_BASENAME", example = "FULL_PATH")
    private String matchMode;
    @Schema(description = "总体结果：READY、ASSET_MISSING、ASSET_AMBIGUOUS、UNSUPPORTED_IMAGE_TYPE", example = "READY")
    private String outcome = "READY";
    @Schema(description = "成功匹配的引用数量", example = "2")
    private int matchedCount;
    @Schema(description = "缺失引用数量", example = "0")
    private int missingCount;
    @Schema(description = "歧义引用数量", example = "0")
    private int ambiguousCount;
    @Schema(description = "不支持图片格式引用数量", example = "0")
    private int unsupportedCount;
    @Schema(description = "是否存在会暂停自动解析的问题", example = "false")
    private boolean blockingIssues;
    @Schema(description = "缺失图片的规范目标路径")
    private List<String> missingPaths = new ArrayList<>();
    @Schema(description = "发生歧义时实际存在的候选文件名")
    private List<String> candidateFilenames = new ArrayList<>();
    @Schema(description = "逐引用匹配明细")
    private List<MarkdownAssetIssueDTO> issues = new ArrayList<>();

    public boolean hasBlockingIssues() {
        return blockingIssues || missingCount > 0 || ambiguousCount > 0 || unsupportedCount > 0;
    }
}

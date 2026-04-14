package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

/**
 * 分页响应
 */
@Data
@Schema(description = "分页响应")
public class PageResult<T> {

    @Schema(description = "数据列表")
    private List<T> items;

    @Schema(description = "总记录数", example = "100")
    private long total;

    @Schema(description = "当前页", example = "1")
    private int page;

    @Schema(description = "每页大小", example = "20")
    private int pageSize;

    public PageResult() {
    }

    public PageResult(List<T> items, long total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    /**
     * 计算总页数
     */
    @Schema(description = "总页数", example = "5")
    public int getTotalPages() {
        if (pageSize <= 0) return 0;
        return (int) Math.ceil((double) total / pageSize);
    }
}
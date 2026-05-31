package com.qingluo.link.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 前端召回请求（camelCase）。
 *
 * <p>首版只接收 {@code query} 与 {@code datasetIds}。用 {@link JsonAnySetter} 收集未知字段，并用
 * {@code @AssertTrue} 在 {@code @Valid} 阶段拒绝 {@code docIds/topK/sources/strict/includeContent} 等
 * （返回 400），避免前端误以为这些策略已生效。</p>
 *
 * <p>注意：不能用 {@code @JsonIgnoreProperties(ignoreUnknown=false)} —— 是否对未知字段抛异常由
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 决定（Spring Boot 默认关闭），该注解并不强制开启。</p>
 */
@Data
@Schema(description = "召回流式请求")
public class RecallStreamRequest {

    @NotBlank(message = "query 不能为空")
    @Schema(description = "用户原始问题", example = "什么是 RAG")
    private String query;

    /**
     * 数据集范围。不可为 {@code null}；空列表表示“当前用户的全部数据集”（Java 侧展开为本人所有 dataset id）。
     */
    @NotNull(message = "datasetIds 不能为 null（可为空列表表示当前用户的全部数据集）")
    @Schema(description = "数据集范围；空列表表示当前用户的全部数据集", example = "[1, 2]")
    private List<Long> datasetIds;

    /** 收集首版不接收的未知字段（docIds/topK/sources/strict/includeContent 等）。 */
    @JsonIgnore
    private final Map<String, Object> unknownFields = new HashMap<>();

    @JsonAnySetter
    public void putUnknownField(String name, Object value) {
        unknownFields.put(name, value);
    }

    @AssertTrue(message = "不支持的字段：首版仅接收 query 与 datasetIds")
    @JsonIgnore
    public boolean isOnlyKnownFields() {
        return unknownFields.isEmpty();
    }
}

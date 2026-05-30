package com.qingluo.link.service.recall;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发往 Python 内部召回接口的请求体（snake_case）。
 *
 * <p>{@code user_id} / {@code dataset_ids} 与内部 JWT 的 {@code sub} / {@code dataset_ids} 同源，由
 * {@code RecallServiceImpl} 用同一份已校验范围构造，保证两者自洽（acceptance 场景 12/13）。</p>
 *
 * <p>放在 link-service（非 link-model）：link-model 主代码无 jackson-databind compile 依赖，而本类仅内部使用。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecallUpstreamRequest {

    private String query;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("dataset_ids")
    private List<Long> datasetIds;
}

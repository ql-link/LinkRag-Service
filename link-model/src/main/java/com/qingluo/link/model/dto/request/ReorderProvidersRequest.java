package com.qingluo.link.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 按管理端展示顺序重排系统厂商。
 */
@Data
@Schema(description = "系统厂商排序请求")
public class ReorderProvidersRequest {

    @NotEmpty
    @Schema(description = "按从上到下顺序排列的完整厂商 ID 列表", required = true)
    private List<@NotNull Long> providerIds;
}

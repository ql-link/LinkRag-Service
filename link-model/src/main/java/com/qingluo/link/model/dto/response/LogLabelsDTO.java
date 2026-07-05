package com.qingluo.link.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端日志筛选标签。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日志筛选标签")
public class LogLabelsDTO {

    @Schema(description = "服务名列表")
    private List<String> services;

    @Schema(description = "日志级别列表")
    private List<String> levels;
}

package com.qingluo.link.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理端日志查询结果。
 */
@Data
@Schema(description = "日志条目")
public class LogEntryDTO {

    @Schema(description = "日志时间")
    private String time;

    @Schema(description = "日志级别")
    private String level;

    @Schema(description = "服务名")
    private String service;

    @Schema(description = "主机名")
    private String host;

    @Schema(description = "进程ID")
    private String pid;

    @JsonProperty("trace_id")
    @Schema(description = "链路追踪ID")
    private String traceId;

    @JsonProperty("logger_name")
    @Schema(description = "Logger 名称")
    private String loggerName;

    @Schema(description = "日志消息")
    private String message;

    @Schema(description = "异常堆栈")
    private String exception;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "原始日志行，仅解析失败时返回")
    private String raw;
}

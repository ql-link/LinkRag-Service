package com.qingluo.link.service.constant;

/**
 * document_parse_pipeline.pipeline_status 的取值常量（DB 中全大写）。
 */
public final class ParsePipelineStatus {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private ParsePipelineStatus() {
    }

    /** 运行中（非终态）：用于"正在解析中，勿重复提交"判定。 */
    public static boolean isRunning(String status) {
        return PENDING.equals(status) || PROCESSING.equals(status);
    }
}

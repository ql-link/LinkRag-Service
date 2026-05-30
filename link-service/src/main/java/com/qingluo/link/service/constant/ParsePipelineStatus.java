package com.qingluo.link.service.constant;

/**
 * document_parse_pipeline.pipeline_status 的取值常量（DB 中全大写）。
 *
 * <p>⚠️ 与 parse_result 消息体的 task_status（小写 success/failed）是两套取值，库侧大写、消息侧小写，禁止混用。</p>
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

    /** 大写 pipeline 终态 → parse_result 消息的小写 task_status；非终态返回 null。 */
    public static String toMessageStatus(String pipelineStatus) {
        if (SUCCESS.equals(pipelineStatus)) {
            return "success";
        }
        if (FAILED.equals(pipelineStatus)) {
            return "failed";
        }
        return null;
    }

    /** 消息侧小写 task_status 是否与库侧大写 pipeline 终态一致。 */
    public static boolean matchesMessageStatus(String messageStatus, String pipelineStatus) {
        String mapped = toMessageStatus(pipelineStatus);
        return mapped != null && mapped.equals(messageStatus);
    }
}

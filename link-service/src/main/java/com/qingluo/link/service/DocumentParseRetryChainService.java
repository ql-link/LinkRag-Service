package com.qingluo.link.service;

import java.util.List;

/**
 * 解析重试链回溯查询（后端能力，不出对外 API）。
 */
public interface DocumentParseRetryChainService {

    /**
     * 按 task_id 沿 document_parsed_log.retry_of_task_id 向 origin 逐跳回溯重试链。
     *
     * <p>返回从入参 task_id 到链起点的有序列表（含入参自身）。设深度上限、防循环；
     * 遇空 / 链断（指向的上一轮日志不存在）安全终止，不抛异常。</p>
     *
     * @param taskId 起点任务标识
     * @return 重试链（入参在前，origin 在后）；入参为空或对应日志不存在时返回空列表
     */
    List<String> traceChain(String taskId);
}

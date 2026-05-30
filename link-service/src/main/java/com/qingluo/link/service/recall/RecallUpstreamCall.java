package com.qingluo.link.service.recall;

/**
 * 正在进行中的上游召回调用句柄。前端断开 SSE 时由 RecallServiceImpl 调用 {@link #cancel()} 取消到 Python 的连接。
 */
@FunctionalInterface
public interface RecallUpstreamCall {

    /** 取消上游调用；对已结束的调用调用应为无副作用。 */
    void cancel();
}

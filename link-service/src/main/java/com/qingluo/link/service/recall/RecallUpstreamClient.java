package com.qingluo.link.service.recall;

/**
 * 调用 Python 内部召回 stream 的客户端抽象。
 *
 * <p>抽象成接口以便单元测试 mock（RecallServiceImpl 不直接依赖 okhttp）。实现负责：构造内部请求、
 * 异步发起调用并立即返回可取消句柄、解析上游 SSE、把结果映射成 {@link RecallUpstreamListener} 的回调。</p>
 */
public interface RecallUpstreamClient {

    /**
     * 异步发起对 Python 的召回调用并立即返回句柄（实际调用在召回转发线程池执行）。
     *
     * @param request   snake_case 请求体（query/user_id/dataset_ids）
     * @param jwt       内部 HS256 JWT（claims 与 request 自洽）
     * @param requestId 请求 ID，用作 X-Request-Id 与 JWT jti
     * @param listener  结果回调
     * @return 可取消句柄；若转发线程池拒绝则抛 {@link java.util.concurrent.RejectedExecutionException}
     */
    RecallUpstreamCall stream(RecallUpstreamRequest request, String jwt, String requestId, RecallUpstreamListener listener);
}

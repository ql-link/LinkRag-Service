package com.qingluo.link.service;

import com.qingluo.link.model.dto.request.RecallSessionRequest;
import com.qingluo.link.model.dto.response.RecallSessionResponse;

/**
 * 召回 session token 签发服务（LINK-104）。
 *
 * <p>Java 退化为「召回授权签发者」：校验登录态用户可用性 + 数据集归属，签发短期 token 供前端直连 Python 召回 SSE。
 * 不代理/中转 SSE 流内容，不做 jti / 一次性 / 防重放 / 撤销。</p>
 */
public interface RecallSessionService {

    /**
     * 签发短期召回 session token。
     *
     * @param userId  当前登录用户 ID
     * @param request 含本次授权的数据集 id 列表（显式非空）
     * @return token、有效期（秒）与前端直连地址
     */
    RecallSessionResponse issue(Long userId, RecallSessionRequest request);
}

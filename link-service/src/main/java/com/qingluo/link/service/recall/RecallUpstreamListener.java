package com.qingluo.link.service.recall;

import com.qingluo.link.model.dto.response.RecallHitDTO;
import com.qingluo.link.model.enums.RecallSseError;
import java.util.List;

/**
 * Python 上游召回结果回调。实现方（RecallServiceImpl）据此驱动前端 SSE。
 *
 * <p>上游协议适配（HTTP 状态、Python error code、超时、未知错误的映射，以及 hits 裁剪）由
 * {@link RecallUpstreamClient} 完成；本回调只暴露两个干净结果：成功的最小候选，或已映射的 SSE 错误码。
 * 每次调用至多触发其一一次。</p>
 */
public interface RecallUpstreamListener {

    /** recall_done：已裁剪为最小候选（chunkId/docId/datasetId），保持上游顺序。 */
    void onDone(List<RecallHitDTO> hits);

    /** 任何失败：已映射为对前端的 SSE 错误码（不含内部堆栈）。 */
    void onError(RecallSseError error);
}

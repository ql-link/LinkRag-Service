package com.qingluo.link.model.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE {@code recall_done} 事件载荷。仅含融合后的最小候选，保持 Python 返回顺序。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecallDoneEvent {

    private List<RecallHitDTO> hits;
}

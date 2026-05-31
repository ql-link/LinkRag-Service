package com.qingluo.link.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE {@code error} 事件载荷。{@code message} 不含内部堆栈。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecallErrorEvent {

    private String code;
    private String message;
}

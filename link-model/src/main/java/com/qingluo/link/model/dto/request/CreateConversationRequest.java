package com.qingluo.link.model.dto.request;

import lombok.Data;

/**
 * 创建对话请求
 */
@Data
public class CreateConversationRequest {

    private String title;
    private Long lastConfigId;
}
package com.qingluo.link.service.impl.llm;

import com.qingluo.link.model.dto.entity.UserLLMConfig;

/**
 * 基于用户 Chat 模型生成对话标题。
 */
public interface ChatTitleGenerator {

    String generate(UserLLMConfig config, String query, String answer);
}

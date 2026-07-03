package com.qingluo.link.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM 调用协议枚举（API 家族）。
 *
 * <p>协议表达「API 家族」而非「厂商」：同一厂商不同模型能力可走不同协议
 * （如千问 chat=openai、rerank=dashscope）。下游按 protocol + capability 选 adapter。
 * code 与库内存值一致，统一小写。</p>
 */
@Getter
@AllArgsConstructor
public enum LLMProtocol {

    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    GOOGLE("google"),
    JINA("jina"),
    DASHSCOPE("dashscope"),
    BGE_M3("bge_m3"),
    DOUBAO_VISION("doubao_vision");

    private final String code;

    private static final Set<String> CODES = Arrays.stream(values())
            .map(LLMProtocol::getCode)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * 大小写敏感校验：库内存小写，"OPENAI" 等非规范写法视为非法，避免同协议出现多种写法。
     */
    public static boolean isValid(String code) {
        return code != null && CODES.contains(code);
    }

    public static String normalizedSet() {
        return CODES.stream().sorted().collect(Collectors.joining(", "));
    }
}

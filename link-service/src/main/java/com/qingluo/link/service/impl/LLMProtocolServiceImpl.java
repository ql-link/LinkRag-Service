package com.qingluo.link.service.impl;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.model.enums.LLMProtocol;
import com.qingluo.link.service.LLMProtocolService;
import org.springframework.stereotype.Service;

/**
 * LLM 调用协议校验服务实现。
 *
 * <p>受支持协议集合由 {@link LLMProtocol} 枚举表达；大小写敏感校验，
 * 库内统一存小写，"OPENAI" 等非规范写法视为非法。</p>
 */
@Service
public class LLMProtocolServiceImpl implements LLMProtocolService {

    @Override
    public void validateProtocol(String protocol) {
        if (!LLMProtocol.isValid(protocol)) {
            throw new BusinessException(ErrorCode.INVALID_PROTOCOL,
                    "协议 [" + protocol + "] 不在支持范围内，合法取值：" + LLMProtocol.normalizedSet());
        }
    }
}

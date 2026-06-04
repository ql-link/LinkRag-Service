package com.qingluo.link.service.impl.llm;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.model.enums.ErrorCode;
import com.qingluo.link.service.config.LlmModelFetchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;

/**
 * 模型列表代理请求的 SSRF 防护。
 */
@Component
@RequiredArgsConstructor
public class ApiBaseUrlGuard {

    private final LlmModelFetchProperties properties;

    public URI validate(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ErrorCode.INVALID_API_BASE_URL);
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme)
                    && !(properties.isAllowHttp() && "http".equalsIgnoreCase(scheme))) {
                throw new BusinessException(ErrorCode.INVALID_API_BASE_URL);
            }
            if (!StringUtils.hasText(uri.getHost()) || StringUtils.hasText(uri.getUserInfo())) {
                throw new BusinessException(ErrorCode.INVALID_API_BASE_URL);
            }
            if (properties.isBlockPrivateAddress()) {
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    rejectPrivateAddress(address);
                }
            }
            return uri;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_API_BASE_URL);
        }
    }

    private void rejectPrivateAddress(InetAddress address) {
        String hostAddress = address.getHostAddress();
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || "169.254.169.254".equals(hostAddress)
                || "0.0.0.0".equals(hostAddress)) {
            throw new BusinessException(ErrorCode.INVALID_API_BASE_URL);
        }
    }
}

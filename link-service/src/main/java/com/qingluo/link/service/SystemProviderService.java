package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.SystemProvider;
import java.util.List;

/**
 * 系统厂商服务接口
 */
public interface SystemProviderService {

    /**
     * 获取所有启用的厂商
     */
    List<SystemProvider> getActiveProviders();

    /**
     * 根据 providerType 获取厂商
     */
    SystemProvider getByProviderType(String providerType);
}
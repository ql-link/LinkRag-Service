package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.response.ProviderModelDTO;
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
     * 按能力查询用户侧可用厂商与模型。
     */
    List<ProviderModelDTO> getActiveProviderModels(String capability);

    /**
     * 根据 providerType 获取厂商
     */
    SystemProvider getByProviderType(String providerType);

    /**
     * 根据 providerType 获取启用厂商。
     */
    SystemProvider getActiveByProviderType(String providerType);
}

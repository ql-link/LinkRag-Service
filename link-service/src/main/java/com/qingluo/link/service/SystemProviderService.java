package com.qingluo.link.service;

import com.qingluo.link.model.entity.SystemProvider;

import java.util.List;

/**
 * 系统厂商服务接口
 */
public interface SystemProviderService {

    /**
     * 获取所有启用的系统厂商列表
     */
    List<SystemProvider> getActiveProviders();

    /**
     * 获取所有厂商列表（包含禁用的）
     */
    List<SystemProvider> listAllProviders();
}
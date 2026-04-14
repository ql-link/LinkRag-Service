package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.SystemProvider;
import com.qingluo.link.model.dto.request.CreateProviderRequest;
import com.qingluo.link.model.dto.request.UpdateProviderRequest;
import com.qingluo.link.model.dto.response.PageResult;

/**
 * 管理员系统厂商管理服务接口
 */
public interface AdminProviderService {

    /**
     * 分页查询厂商列表
     */
    PageResult<SystemProvider> listProviders(int page, int size);

    /**
     * 创建厂商
     */
    void createProvider(CreateProviderRequest request);

    /**
     * 更新厂商
     */
    void updateProvider(Long id, UpdateProviderRequest request);

    /**
     * 删除厂商
     */
    void deleteProvider(Long id);

    /**
     * 启用/禁用厂商
     */
    void toggleActive(Long id, boolean isActive);
}

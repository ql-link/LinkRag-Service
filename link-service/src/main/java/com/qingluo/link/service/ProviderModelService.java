package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.ProviderModel;

import java.util.List;

/**
 * 厂商模型能力目录服务。
 *
 * <p>「厂商→模型→能力」三层的中间层访问入口，取代原 supported_models JSON 解析：
 * 对用户侧提供按能力的目录查询，对管理端提供模型能力的增删与上下架。</p>
 */
public interface ProviderModelService {

    /**
     * 查某厂商的上架模型能力行，可按能力过滤（capability 为空表示不过滤）。
     * 配置厂商时用它展开整厂商全部 (模型, 能力)。
     */
    List<ProviderModel> listActiveModels(Long providerId, String capability);

    /**
     * 批量查多个厂商的上架模型能力行，可按能力过滤（capability 为空表示不过滤）。
     * 用户侧目录查询用它一次性取回全部厂商模型，避免逐厂商查询的 N+1。
     * providerIds 为空时返回空列表，不触达数据库。
     */
    List<ProviderModel> listActiveModelsByProviderIds(List<Long> providerIds, String capability);

    /**
     * 校验某厂商下某模型是否支持某能力且处于上架状态。
     * 按能力选生效模型前用它拦截「模型不支持该能力」。
     */
    boolean isModelCapabilityActive(Long providerId, String modelName, String capability);

    /**
     * 管理端：新增一条模型能力（已存在则幂等确保上架）。
     */
    ProviderModel addModelCapability(Long providerId, String modelName, String capability);

    /**
     * 管理端：删除一条模型能力目录项。
     */
    void deleteModelCapability(Long id);

    /**
     * 管理端：上/下架某条模型能力。
     */
    void toggleModelCapability(Long id, boolean isActive);
}

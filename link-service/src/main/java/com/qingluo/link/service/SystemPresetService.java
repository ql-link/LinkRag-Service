package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.request.CreatePresetRequest;

import java.util.List;

/**
 * 系统预设服务。
 *
 * <p>管理员预配整套可用配置（自带平台 Key）；用户注册时把 active 预设复制进用户配置表，
 * 作为常备只读备选实现开箱即用。</p>
 */
public interface SystemPresetService {

    /**
     * 注册时把全部 active 系统预设复制进该用户的配置表（is_system_preset=true）。
     * 同一能力只让首条预设生效（is_default=true），其余作为备选；重复调用幂等不重复灌入。
     */
    void applyPresetsForNewUser(Long userId);

    /**
     * 管理端：新增一条系统预设（平台 Key 入库前加密）。
     */
    SystemPreset createPreset(CreatePresetRequest request);

    /**
     * 管理端：删除一条系统预设。
     */
    void deletePreset(Long id);

    /**
     * 管理端：列出全部系统预设（Key 脱敏返回）。
     */
    List<SystemPreset> listPresets();
}

package com.qingluo.link.service;

import com.qingluo.link.model.dto.entity.SystemPreset;
import com.qingluo.link.model.dto.request.CreatePresetRequest;
import com.qingluo.link.model.dto.request.UpdatePresetRequest;

import java.util.List;

/**
 * 系统预设服务。
 *
 * <p>管理员预配 LinkRag 系统兜底配置（自带平台 Key）；用户没有自配生效模型时，
 * Java 回退到 active + default 的系统预设。</p>
 */
public interface SystemPresetService {

    /**
     * 管理端：新增一条系统预设（平台 Key 入库前加密）。
     */
    SystemPreset createPreset(CreatePresetRequest request);

    /**
     * 管理端：删除一条系统预设。
     */
    void deletePreset(Long id);

    /**
     * 管理端：更新一条系统预设。
     */
    SystemPreset updatePreset(Long id, UpdatePresetRequest request);

    /**
     * 管理端：启用/禁用一条系统预设。
     */
    void togglePreset(Long id, boolean isActive);

    /**
     * 管理端：把一条系统预设设为其能力的系统兜底默认，并解除同能力其他默认。
     */
    void setDefaultPreset(Long id);

    /**
     * 管理端：列出全部系统预设（Key 脱敏返回）。
     */
    List<SystemPreset> listPresets();
}

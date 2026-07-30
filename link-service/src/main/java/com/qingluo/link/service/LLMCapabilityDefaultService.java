package com.qingluo.link.service;

import com.qingluo.link.model.dto.response.CapabilityDefaultDTO;
import java.util.List;

public interface LLMCapabilityDefaultService {

    CapabilityDefaultDTO getDefault(Long userId, String capability);

    List<CapabilityDefaultDTO> listDefaults(Long userId);

    CapabilityDefaultDTO setUserDefault(Long userId, String capability, Long configId);

    CapabilityDefaultDTO clearUserDefault(Long userId, String capability);

    void clearUserDefaultForConfig(Long userId, Long configId);

    void clearUserDefaultsForConfig(Long configId);
}

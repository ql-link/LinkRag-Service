package com.qingluo.link.service.cache;

import com.qingluo.link.model.dto.response.UserLLMConfigDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户 LLM 配置缓存值。
 *
 * <p>用具体包裹类型承载 DTO 列表，避免 Redis 反序列化时 List 泛型擦除。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLLMConfigSnapshot {

    private List<UserLLMConfigDTO> configs;
}

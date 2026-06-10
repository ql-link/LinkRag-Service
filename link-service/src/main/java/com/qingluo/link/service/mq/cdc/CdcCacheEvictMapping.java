package com.qingluo.link.service.mq.cdc;

import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.service.cache.ProviderCatalogCacheService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * CDC 缓存补偿的统一映射声明：表 → [(缓存目标, route_id 取法)]。
 *
 * <p>差异收敛到每个映射项的「取法」（{@link RouteIdResolver}），展开循环对所有表零分支：
 * 直取=读变更行某字段；解析=读外键再查字典换算。新增缓存表只需往 {@link #rules} 加一行，
 * 不动消费/展开逻辑。核心不变式：每张被监听表的 route_id 都能由「本行字段 + 至多一次字典查询」确定。</p>
 */
@Component
public class CdcCacheEvictMapping {

    /** 从一行变更（列名→值）解析出 route_id；返回 null 表示该条降级跳过。 */
    @FunctionalInterface
    public interface RouteIdResolver {
        String resolve(Map<String, String> row);
    }

    /** 一条映射项：删哪类缓存 + 怎么取 route_id。 */
    public record MappingRule(CacheEvictTarget target, RouteIdResolver resolver) {
    }

    private final ProviderCatalogCacheService providerCatalog;
    private final Map<String, List<MappingRule>> rules;

    public CdcCacheEvictMapping(ProviderCatalogCacheService providerCatalog) {
        this.providerCatalog = providerCatalog;
        this.rules = Map.of(
                "sys_user", List.of(
                        new MappingRule(CacheEvictTarget.USER, direct("id"))),
                "llm_user_config", List.of(
                        new MappingRule(CacheEvictTarget.LLM_CONFIG, direct("id")),
                        new MappingRule(CacheEvictTarget.USER_DEFAULT_LLM_CONFIG, direct("user_id"))),
                "llm_system_provider", List.of(
                        new MappingRule(CacheEvictTarget.SYSTEM_PROVIDER, direct("provider_type"))),
                "llm_provider_model", List.of(
                        new MappingRule(CacheEvictTarget.SYSTEM_PROVIDER,
                                row -> resolveProviderType(row.get("provider_id")))));
    }

    /** 该表的映射项；未配置（无对应缓存目标）返回空列表，调用方忽略。 */
    public List<MappingRule> rulesOf(String table) {
        return rules.getOrDefault(table, List.of());
    }

    /**
     * 直取取法：route_id 即变更行某字段值。被监听表必含该列，缺失即 Canal 行数据损坏，
     * 抛 {@link IllegalArgumentException} 当坏消息（错误处理器判不可重试、跳过告警），
     * 区别于解析取法返回 null 的业务降级。
     */
    private RouteIdResolver direct(String field) {
        return row -> {
            String value = row.get(field);
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("canal row missing field: " + field);
            }
            return value;
        };
    }

    /**
     * 解析取法：外键 provider_id → 只读厂商索引缓存换 provider_type。
     * 查不到（索引未命中或厂商停用/删除）返回 null 触发降级；provider_id 非数字属脏数据，
     * 任由 NumberFormatException 冒泡给错误处理器按坏消息跳过+告警。
     */
    private String resolveProviderType(String providerId) {
        if (!StringUtils.hasText(providerId)) {
            return null;
        }
        return providerCatalog.resolveProviderTypeById(Long.valueOf(providerId));
    }
}

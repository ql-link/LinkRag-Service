package com.qingluo.link.service.mq.cdc;

import com.qingluo.link.components.redis.service.CacheEvictTarget;
import org.springframework.stereotype.Component;

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

    private final Map<String, List<MappingRule>> rules = Map.of();

    /** 该表的映射项；未配置（无对应缓存目标）返回空列表，调用方忽略。 */
    public List<MappingRule> rulesOf(String table) {
        return rules.getOrDefault(table, List.of());
    }
}

package com.qingluo.link.service.mq.cdc;

import com.qingluo.link.components.redis.service.CacheEvictTarget;
import com.qingluo.link.service.cache.LLMRuntimeCacheReadiness;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * CDC 失效路由只读取当前行、old image 或声明式全局常量。
 */
@Component
public class CdcCacheEvictMapping {

    private static final Set<String> LOGIN_ONLY_FIELDS =
        Set.of("last_login_at", "last_login_time", "updated_at");

    @FunctionalInterface
    public interface RouteIdResolver {
        RouteResolution resolve(ChangeRow row);
    }

    public record MappingRule(CacheEvictTarget target, RouteIdResolver resolver) {
    }

    public record ChangeRow(String operation, Map<String, String> current, Map<String, String> before) {
    }

    public record RouteResolution(List<String> routeIds, boolean ignored) {

        static RouteResolution routes(List<String> routeIds) {
            return new RouteResolution(routeIds, false);
        }

        static RouteResolution skip() {
            return new RouteResolution(List.of(), true);
        }
    }

    private final LLMRuntimeCacheReadiness llmRuntimeCacheReadiness;
    private final Map<String, List<MappingRule>> rules;

    public CdcCacheEvictMapping(LLMRuntimeCacheReadiness llmRuntimeCacheReadiness) {
        this.llmRuntimeCacheReadiness = llmRuntimeCacheReadiness;
        this.rules = Map.of(
            "dataset_parse_config", List.of(new MappingRule(
                CacheEvictTarget.DATASET_PARSE_CONFIG, this::datasetRoutes)),
            "sys_user", List.of(new MappingRule(
                CacheEvictTarget.USER_PROFILE, this::userRoutes)),
            "blog_post", List.of(new MappingRule(
                CacheEvictTarget.PUBLISHED_BLOG_INDEX,
                row -> RouteResolution.routes(List.of("global")))),
            "blog_asset", List.of(new MappingRule(
                CacheEvictTarget.PUBLISHED_BLOG_INDEX,
                row -> RouteResolution.routes(List.of("global")))),
            "llm_model_config", List.of(new MappingRule(
                CacheEvictTarget.LLM_RUNTIME_CONFIG, this::llmRuntimeRoutes))
        );
    }

    public List<MappingRule> rulesOf(String table) {
        if (!StringUtils.hasText(table)) {
            return List.of();
        }
        String normalizedTable = table.toLowerCase(Locale.ROOT);
        if ("llm_model_config".equals(normalizedTable)
            && !llmRuntimeCacheReadiness.isMappingEnabled()) {
            return List.of();
        }
        return rules.getOrDefault(normalizedTable, List.of());
    }

    private RouteResolution datasetRoutes(ChangeRow row) {
        LinkedHashSet<String> routes = new LinkedHashSet<>();
        addIfPresent(routes, row.current().get("dataset_id"));
        addIfPresent(routes, row.before().get("dataset_id"));
        return RouteResolution.routes(new ArrayList<>(routes));
    }

    private RouteResolution userRoutes(ChangeRow row) {
        if ("UPDATE".equals(row.operation()) && !row.before().isEmpty()
            && LOGIN_ONLY_FIELDS.containsAll(row.before().keySet().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList())) {
            return RouteResolution.skip();
        }
        String id = firstText(row.current().get("id"), row.before().get("id"));
        return RouteResolution.routes(StringUtils.hasText(id) ? List.of(id) : List.of());
    }

    private RouteResolution llmRuntimeRoutes(ChangeRow row) {
        Map<String, String> image = "DELETE".equals(row.operation()) ? row.before() : row.current();
        String id = image.get("id");
        return RouteResolution.routes(StringUtils.hasText(id) ? List.of(id) : List.of());
    }

    private void addIfPresent(Set<String> routes, String value) {
        if (StringUtils.hasText(value)) {
            routes.add(value);
        }
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}

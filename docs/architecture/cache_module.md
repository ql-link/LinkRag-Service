# Cache Module

## 职责

Redis 能力由 `link-components/toLink-components-redis` 提供，业务缓存封装位于 `link-service/src/main/java/com/qingluo/link/service/cache`。

## 当前能力

- `CacheReadProtectionService`：缓存读保护、空值缓存、TTL 抖动。
- `CacheConsistencyService`：同步删缓存与补偿删除。
- `CacheKeyRouter` / `CacheEvictTarget`：统一缓存 key 路由。
- 业务封装：`UserCacheService`、`UserLLMConfigCacheService`、`KnowledgeFileConfigCacheService`。

## 约定

业务代码不直接散落 Redis key 拼接。新增缓存目标优先扩展 `CacheEvictTarget` 和 `CacheKeyRouter`。

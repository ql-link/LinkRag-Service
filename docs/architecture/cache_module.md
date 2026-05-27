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

## 可用性与一致性边界

- 用户资料缓存读取失败时按缓存未命中处理；数据库回源已成功但 Redis 回填失败时直接返回回源结果，不因缓存不可用阻断登录和资料查询。
- 数据库回源本身失败时保留原异常且不重复执行 loader，避免将数据库错误误判为缓存降级场景。
- 写库后的同步删缓存仍由 `CacheConsistencyService` 控制；当 `sync-delete-required=true` 时，删除失败必须向上抛出，不能由业务缓存封装吞掉。
- `tolink.cache.evict` 补偿消费失败继续交由消息消费重试机制处理。

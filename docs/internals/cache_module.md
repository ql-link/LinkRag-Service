# Cache Module

## 职责

Redis 能力由 `link-components/toLink-components-redis` 提供，业务缓存封装位于 `link-service/src/main/java/com/qingluo/link/service/cache`。

## 当前能力

- `CacheReadProtectionService`：缓存读保护、空值缓存、TTL 抖动。
- `CacheConsistencyService`：同步删缓存与补偿删除。
- `CacheKeyRouter` / `CacheEvictTarget`：统一缓存 key 路由。
- 业务封装：`UserCacheService`、`UserLLMConfigCacheService`、`DocumentFileConfigCacheService`。

## 约定

业务代码不直接散落 Redis key 拼接。新增缓存目标优先扩展 `CacheEvictTarget` 和 `CacheKeyRouter`。

## Document file upload runtime config

- Runtime key: `document:file-upload:config`.
- Default source: `tolink.document-file.*`.
- Startup initialization writes the default config to Redis only when the key is absent.
- Admin updates write Redis directly. A Redis write failure must fail the admin update.
- Upload validation reads Redis first. Missing, unreadable, or invalid Redis config falls back to application defaults.
- The historical `document_file_config` table has been removed; there is no database fallback for this runtime config.

## 可用性与一致性边界

- 用户资料缓存读取失败时按缓存未命中处理；数据库回源已成功但 Redis 回填失败时直接返回回源结果，不因缓存不可用阻断登录和资料查询。
- 数据库回源本身失败时保留原异常且不重复执行 loader，避免将数据库错误误判为缓存降级场景。
- 写库后的第一次删缓存仍由 `CacheConsistencyService` 控制：
  - 处在 Spring 事务中时，首删延后到 `afterCommit` 执行，避免事务未提交前提前制造 cache miss。
  - 不处在事务中时，首删仍在数据库写成功后立即执行。
  - 只要数据库写已经成功，首删失败统一只记录日志并依赖补偿链路最终收敛，不再改变请求结果。
- 这与历史行为不同：过去首删失败是否抛错由 `sync-delete-required` 控制；现在该开关仅兼容保留现有配置绑定，不再决定主流程首删失败是否抛错。
- `tolink.cache.evict` 补偿消费失败继续交由消息消费重试机制处理。

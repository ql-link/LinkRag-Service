# Cache Module

## 职责

Redis 能力由 `link-components/toLink-components-redis` 提供，业务缓存封装位于 `link-service/src/main/java/com/qingluo/link/service/cache`。

## 当前能力

- `CacheReadProtectionService`：缓存读保护、空值缓存、TTL 抖动。
- `CacheConsistencyService`：同步删缓存与补偿删除。
- `CacheKeyRouter` / `CacheEvictTarget`：统一缓存 key 路由。
- 业务封装：`UserCacheService`、`UserLLMConfigCacheService`、`DocumentFileConfigCacheService`、`ProviderCatalogCacheService`。

## 约定

业务代码不直接散落 Redis key 拼接。新增缓存目标优先扩展 `CacheEvictTarget` 和 `CacheKeyRouter`。

## Provider catalog cache（用户侧 LLM 厂商目录缓存）

- 接口 `GET /api/v1/llm/providers` 经 `SystemProviderServiceImpl.getActiveProviderModels` 读取，由 `ProviderCatalogCacheService` 提供读缓存。
- Cache key：`llm:pvd:catalog`，全量一份。缓存「厂商 + 模型」原始快照 `ProviderCatalogSnapshot`（全部启用厂商及其全部上架模型，不按 capability 过滤）。
- capability（CHAT/EMBEDDING/...）过滤与「厂商→模型→能力」聚合在缓存命中后于内存完成，各 capability 共享同一份缓存，避免按 capability 分散成多个 key 难以失效。capability 合法性校验在进入缓存前完成，非法值直接抛错，不触发回源、不污染缓存。
- 命中缓存时 0 次 DB；未命中回源 2 次查询（启用厂商 + IN 批量查模型）构建快照并回填，回填复用 `CacheReadProtectionService`（空值占位、单 key 锁合并回源、TTL 抖动）。
- TTL：1 小时，仅作兜底自愈；目录为低频字典数据，变更靠写路径 evict 主动失效。
- 失效：写路径（`AdminProviderService` 厂商增删改启停、`ProviderModelService` 模型能力增删上下架）沿用既有 `cacheConsistencyService.evict(SYSTEM_PROVIDER, providerType)`。`CacheKeyRouter` 对 `SYSTEM_PROVIDER` 在单厂商 key 之外连带返回 `llm:pvd:catalog`，故任意厂商/模型变更都会失效全量目录缓存，无需在写路径逐处追加调用。
- 缓存不可用时按「数据库回源优先」降级，不因 Redis 抖动阻断用户侧目录查询。

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

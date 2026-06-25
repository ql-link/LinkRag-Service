# Cache Module

## 职责

Redis 基础能力由 `link-components/toLink-components-redis` 提供，业务缓存封装位于 `link-service/src/main/java/com/qingluo/link/service/cache`。

## 当前能力

通用缓存基础设施在 `link-components/toLink-components-redis/.../service`：

- `CacheReadProtectionService`：缓存读保护、空值缓存、TTL 抖动。
- `CacheConsistencyService`：同步删缓存与补偿删除。
- `CacheKeyRouter` / `CacheEvictTarget`：统一缓存 key 路由。

业务缓存封装在 `link-service/.../service/cache`：`UserCacheService`、`DocumentFileConfigCacheService`、`ProviderCatalogCacheService`。

## 约定

业务代码不直接散落 Redis key 拼接。新增缓存目标优先扩展 `CacheEvictTarget` 和 `CacheKeyRouter`。

## Provider catalog cache（用户侧 LLM 厂商目录缓存）

- 接口 `GET /api/v1/llm/providers` 经 `SystemProviderServiceImpl.getActiveProviderModels` 读取，由 `ProviderCatalogCacheService` 提供读缓存。
- 缓存按「索引 + 按厂商分片」组织，避免单一大 key（旧版 `llm:pvd:catalog` 全量快照约 150KB）：
  - `llm:pvd:catalog:index`：启用厂商索引 `ProviderCatalogIndex`（轻量引用 `ProviderRef` 列表，含 id/type/name/priority，priority 倒序），不含模型，约数 KB。
  - `llm:pvd:catalog:models:{providerType}`：单厂商模型分片 `ProviderModelShard`（该厂商全部上架模型，全 capability 未过滤），每个约数 KB。分片值 `models` 为 `List<ProviderModel>`，随 `ProviderModel` 实体新增 `protocol` / `api_base_url`（模型能力层事实字段，见 `docs/api/mysql_schema.md`「协议与入口三层语义」）自动携带，无需改缓存代码——这两字段会随分片一起命中缓存并组装进 `GET /api/v1/llm/providers` 响应的每个 `ModelCapabilityDetailDTO`。
  - 索引与分片均为具体包裹类型，绕开缓存反序列化对 `List<...>` 的泛型擦除。
- 读取：先读索引拿启用厂商（单 key 读保护），再用 `CacheReadProtectionService.getOrLoadBatch` 一次 MGET 批量读各厂商分片；缺失分片按 type→id 回源（`listActiveModelsByProviderIds`）分组回填，合并为 `ProviderCatalogSnapshot` 返回。
- capability（CHAT/EMBEDDING/...）过滤与「厂商→模型→能力」聚合在缓存命中后于内存完成，各 capability 共享同一份缓存；capability 合法性校验在进入缓存前完成，非法值直接抛错，不触发回源、不污染缓存。
- 命中缓存时 0 次 DB；分片回填复用 `CacheReadProtectionService`（空值占位防穿透、TTL 抖动防雪崩）。批量读保护不做单 key 回源合并（击穿）。
- TTL：1 小时，仅作兜底自愈；目录为低频字典数据，变更靠写路径 evict 主动失效。
- 失效：写路径（`AdminProviderService` 厂商增删改启停、`ProviderModelService` 模型能力增删上下架）沿用既有 `cacheConsistencyService.evict(SYSTEM_PROVIDER, providerType)`。`CacheKeyRouter` 对 `SYSTEM_PROVIDER` 在单厂商 key 之外连带返回该厂商模型分片 `llm:pvd:catalog:models:{providerType}` 与厂商索引 `llm:pvd:catalog:index`，故该厂商模型变更或厂商增减都会失效对应分片与索引（改一家只重建一家），无需在写路径逐处追加调用。
- 容错分层：索引读回源完成但回填失败时返回已加载值（不浪费已查数据）；其余缓存异常（索引读穿透、分片 MGET 读故障）整体降级为全量查库，不因 Redis 抖动阻断用户侧目录查询；分片回填故障由批量读保护内部吞掉。
- 发布刷新（协议改造发布期一次性动作）：`ProviderModel` 新增 `protocol` / `api_base_url` 后，上线前缓存中的旧序列化分片不含这两字段，反序列化会得到 `null protocol`，可能被 `setup-provider` 读到并污染用户配置快照。发布步骤须刷新（删除）`llm:pvd:catalog:*` 键（索引 `llm:pvd:catalog:index` + 各厂商分片 `llm:pvd:catalog:models:*`），强制下一次读回源重建带新字段的分片；或为 `MODELS_KEY_PREFIX` 加版本号使旧 key 自然失效。日常字段写入无此问题，写路径 evict 已覆盖。

## Provider 索引只读解析（CDC 补偿复用）

- `ProviderCatalogCacheService.resolveProviderTypeById(id)`：只读厂商索引 `llm:pvd:catalog:index`（`ProviderRef` 含 id + providerType），内存按 id 匹配 providerType；**不回源、不查库**，供 CDC 缓存补偿的解析取法复用。索引未命中（重建态）或 id 不在启用索引（厂商停用/删除）返回 null，由调用方降级跳过。
- `CacheReadProtectionService.getIfPresent(key, clazz)`：只读缓存入口，命中返回反序列化值，未命中或空值占位返回 null，不回源、不写缓存——避免只读旁路误触发回源查库。

## User LLM config cache（用户 LLM 配置缓存）

- 接口 `GET /api/v1/llm/configs` 与 `GET /api/v1/llm/configs/default` 经 `UserLLMConfigServiceImpl` 读取，由 `UserLLMConfigCacheService` 提供 read-through 缓存。
- 缓存 key：`llm:u_cfg:{userId}`，缓存值为 `UserLLMConfigSnapshot`，内部保存该用户全部 `UserLLMConfigDTO` 列表。DTO 中只包含脱敏后的 `apiKeyMasked`，不缓存明文 Key 或加密 Key。
- 读取：缓存未命中时按 `user_id` 一次性回源 `llm_user_config` 全量配置并转 DTO；`providerType` / `capability` / `isActive` 过滤，以及默认配置筛选（`capability + is_default=true + is_active=true`）均在内存完成，避免不同筛选条件拆出多份缓存。
- TTL：1 小时，仅作兜底自愈；真实一致性依赖写路径删缓存。
- 失效：用户 LLM 配置写路径继续调用 `cacheConsistencyService.evict(USER_DEFAULT_LLM_CONFIG, userId)`。`CacheKeyRouter` 对该目标同时删除历史 key `llm:u_def:{userId}` 与当前列表缓存 `llm:u_cfg:{userId}`；CDC 对 `llm_user_config` 的 `USER_DEFAULT_LLM_CONFIG(user_id)` 补偿也复用同一路由。

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

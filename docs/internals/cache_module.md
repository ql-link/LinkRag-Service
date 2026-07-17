# Redis 缓存与一致性

Redis 通用能力由 `link-components/toLink-components-redis` 提供。本期只恢复边界明确的数据库镜像缓存，并把文档上传运行时配置作为独立控制面值保存到 Redis；两者的 TTL、失效和故障语义不同。

## 缓存范围

| Owner | 数据 key | 基础 TTL | 缓存内容 | 失效目标 |
| --- | --- | --- | --- | --- |
| 数据集解析配置 | `cache:dataset:parse-config:{datasetId}` | 7 天 | `DatasetParseConfigResponse` 安全投影 | `dataset_parse_config:{datasetId}` |
| 用户资料 | `cache:user:profile:{userId}` | 1 天 | 当前用户资料 DTO；仅用于资料展示 | `user_profile:{userId}` |
| 公开博客发布索引 | `cache:blog:published-index` | 1 天 | 前 100 条已发布文章轻量列表项和总数 | `published_blog_index:global` |

正常值在基础 TTL 上增加 `0..tolink.cache-consistency.ttl-jitter-seconds` 秒抖动；空值占位默认 60 秒。key 不包含 `v1` / `v2` 等人工版本段。

公开博客 Markdown 正文、图片二进制、密码、授权结果、API Key 明文或密文、LLM 配置、模型选择结果、文档解析状态、用量、管理统计、会话消息和任意筛选分页均不进入本期 Redis 业务缓存。

## 读取保护

`CacheReadProtectionService` 对单 key 使用：

1. Redis 命中直接返回；空值使用短 TTL 占位。
2. 未命中时先用 JVM 本地锁合并同实例并发，再尝试 `cache:lock:*` 分布式加载锁。
3. 回源前读取 `cache:fence:*`，回填时通过 Lua 仅在 fence 未变化时写入，阻止变更期间的慢查询把旧值重新写回缓存。
4. Redis 读取、协调或回填失败时返回 MySQL 结果，不让缓存故障改变查询接口结果，并记录 `tolink.cache.read` / `tolink.cache.write` 指标。

每个数据 key 对应：

- fence：把 `cache:` 前缀替换为 `cache:fence:`，默认保留 30 天。
- load lock：把 `cache:` 前缀替换为 `cache:lock:`，默认 5 秒。

## 写入与删除

数据库镜像缓存采用 cache-aside：

- 业务写入只更新 MySQL，不直接写缓存。
- 有事务时，首次失效收集同事务内的目标并去重，只在 `afterCommit` 执行；回滚不删。
- 无事务时在数据库写成功后立即失效。
- 失效操作通过 Lua 原子执行 `fence++ + fence EXPIRE + DEL dataKey`。
- 首次失效失败不改变已经成功的数据库写请求，只记录错误；CDC/MQ 补偿删除失败则抛错，由 Kafka 重试。

## CDC/binlog 映射

CDC bridge 只允许从当前 binlog 行、old image 或声明式全局范围解析路由，不查询 Redis 辅助索引，也不回查数据库：

| 表 | 路由 |
| --- | --- |
| `dataset_parse_config` | 当前/旧 `dataset_id` → 数据集解析配置 |
| `sys_user` | `id` → 用户资料；仅 `last_login_at` / `last_login_time` / `updated_at` 变化时忽略 |
| `blog_post` | 全局发布索引 |
| `blog_asset` | 全局发布索引 |

已映射表的空行数组、缺失 route、未知 DML、发送耗尽，以及补偿端的坏载荷、未知 target、删除耗尽都会写入 `cache_replay_event`。原 Kafka 记录保持未确认；管理员通过 `/api/v1/admin/cache-replay-events` 重放或显式忽略后，原记录再次到达才允许跳过并提交。

`event_id` 由源 topic、partition、offset、row index、target、route 组成，重复投递保持稳定；同一事件内重复目标先去重。

## 启用门禁与发布顺序

数据库镜像缓存仅在以下条件全部满足时启用：

- `tolink.cache-consistency.enabled=true`
- `tolink.business-cache.enabled=true`
- `tolink.cache-consistency.cdc.enabled=true`
- `cdc.mappings-enabled=true`
- `cdc.consumer-targets-ready=true`
- `cdc.database` 和 `cdc.source-topic` 已配置

建议滚动发布顺序：

1. 先部署识别新 target、具备重试和重放能力的补偿消费者。
2. 再启用 CDC bridge 与表映射。
3. 最后打开数据库镜像缓存总开关。

这避免旧消费者遇到新 target 后静默提交。`BusinessCacheHealthIndicator` 会暴露 readiness 原因。

## 文档上传运行时配置

`runtime:document-file:upload-config` 是管理员覆盖值，不是 MySQL 镜像：

- 使用单次 `SET` 写入完整配置，不设置 TTL。
- key 缺失时使用部署默认值且不自动回写。
- Redis 故障或值损坏时优先使用实例内最后有效快照，否则回退部署默认值。
- `runtime:document-file:default-fingerprint` 保存部署默认配置指纹，用于发现多实例默认值不一致；同样不设置 TTL。
- 这两个 key 不参加 CDC 映射、业务缓存失效或历史 key 清理。

## 历史 key 清理

`scripts/cleanup_legacy_business_cache_keys.sh` 只用于旧版本业务 key。脚本默认 dry-run，显式传入 `--apply` 才删除；使用 Redis `SCAN` 与 `UNLINK`，不会执行 `KEYS`、`FLUSHDB`，也不会匹配 `recall:concurrent:*`。

历史白名单包括旧 LLM、用户资料和旧上传配置 key。新 key `cache:*`、`runtime:document-file:*` 不在该脚本的清理范围。

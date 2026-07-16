# Redis 基础设施

Redis 通用能力仍由 `link-components/toLink-components-redis` 提供，但当前不再缓存 MySQL 业务数据或文档上传配置。

## 当前数据源

| 数据 | 当前读取来源 |
| --- | --- |
| 用户资料、角色 | `sys_user` |
| 用户 LLM 配置 | `llm_user_config` |
| 系统厂商和模型目录 | `llm_system_provider`、`llm_provider_model` |
| LinkRag 系统预设 | `llm_system_preset` |
| 文档上传限制和后缀 | `DocumentFileProperties`（`tolink.document-file.*`） |

这些读路径不访问 Redis，也不在数据库写入后回填或驱逐 Redis。API Key 只以数据库密文为事实来源；Java 对外返回脱敏值，Python 从 MySQL 读取后仅在调用模型前解密。

## 保留的基础架构

- `RedisTemplate`、序列化和连接配置仍保留，供非数据库镜像型 Redis 能力及未来独立缓存使用。
- `CacheReadProtectionService`、`CacheConsistencyService`、`CacheCompensationMQ` 与 CDC bridge 保留为基础骨架。
- `CacheEvictTarget` 当前没有业务 target，`CacheKeyRouter.route` 当前返回空列表。
- `CdcCacheEvictMapping` 当前没有表映射，因此 `sys_user`、LLM 四张表的变更不会再产生缓存补偿消息。
- `tolink.cache.evict` topic 和消费者代码暂不物理删除；发布新版本前应停止旧生产端并排空旧消息，避免历史 target 被新版本拒绝。

新增数据库镜像缓存必须作为独立需求设计数据所有权、失效策略、加密字段边界和两端契约，不应直接恢复本次移除的 key。

## 历史 key 清理

发布后使用 `scripts/cleanup_legacy_business_cache_keys.sh` 清理历史 key。脚本默认 dry-run，显式传入 `--apply` 才删除；使用 Redis `SCAN` 与 `UNLINK`，不会执行 `KEYS`、`FLUSHDB`，也不会匹配 `recall:concurrent:*`。

白名单范围：

- `llm:u_cfg:*`、`llm:u_def:*`、`llm:cfg:*`、`llm:pvd:*`
- `llm:user:*`、`llm:system:*`
- `user:info:*`、`user:role:*`
- `document:file-upload:config`、`knowledge:file-upload:config`

连接参数来自 `REDIS_HOST`、`REDIS_PORT`、`REDIS_DB`、可选 `REDIS_USER`；密码通过 `REDISCLI_AUTH` 传入，脚本不会打印密码。

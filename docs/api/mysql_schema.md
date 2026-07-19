# MySQL Schema

生产共享库的结构和初始化数据以 Python 仓库 Alembic migration 为唯一权威；本次统一 LLM 配置对应 migration `0036`。`scripts/db/init.sql` 与 `link-api/src/main/resources/schema.sql` 仅是 Java 本地 MySQL/H2 镜像，必须与 Python migration 保持字段、默认值、索引和删除语义一致。Java Entity 位于 `link-model/src/main/java/com/qingluo/link/model/dto/entity`。

## 核心表

| 表 | Entity | 业务域 |
| --- | --- | --- |
| `sys_user` | `SysUser` | 用户、角色、状态 |
| `user_login_event` | `UserLoginEvent` | 成功登录事件与活跃统计 |
| `llm_system_provider` | `SystemProvider` | 厂商目录与新增模型模板 |
| `llm_provider_model` | `ProviderModel` | 厂商→模型→能力正式目录 |
| `llm_provider_model_sync_job` | `ProviderModelSyncJob` | 外部模型目录同步任务，不参与运行 |
| `llm_provider_model_sync_candidate` | `ProviderModelSyncCandidate` | 待审核的外部模型候选，不参与运行 |
| `llm_model_config` | `LLMModelConfig` | SYSTEM/USER 共用的可执行配置与全局 `configId` |
| `llm_capability_default` | `LLMCapabilityDefault` | 按 scope、owner、capability 维护默认选择 |
| `dataset_parse_config` | `DatasetParseConfig` | 数据集五类模型绑定与四类解析/召回 JSON 配置 |
| `dataset` | `Dataset` | 数据集 |
| `chat_conversation` | `ChatConversation` | 对话及最后使用的全局配置 ID |
| `chat_message` | `ChatMessage` | 一行一轮的对话消息及本轮全局配置 ID |
| `llm_usage_log` | `UsageLog` | 全链路模型调用账本及全局配置 ID |
| `document_original_file` | `DocumentOriginalFile` | 原始文件上传事实 |
| `document_parse_file` | `DocumentParseFile` | 文件级解析聚合及最新任务指针 |
| `document_parsed_log` | `DocumentParsedLog` | Python 写入的解析产物日志 |
| `document_parse_pipeline` | `DocumentParsePipeline` | Python 写入的后处理流水线终态 |
| `kb_document_chunk` | `KbDocumentChunk` | Python 写入的 Chunk 真值，Java 只读 |
| `blog_post` | `BlogPost` | 博客文章元数据与 Markdown 对象指针 |
| `blog_asset` | `BlogAsset` | 博客公开资源元数据 |
| `user_feedback` | `UserFeedback` | 匿名反馈与处理状态 |

## 统一 LLM 配置

### `llm_model_config`

`llm_model_config.id` 是三端唯一的配置身份。SYSTEM 与 USER 不再分别落在系统预设表和用户配置表；两张旧表及旧数据由 Python migration 直接删除，不做兼容迁移。

| 列 | 约束与语义 |
| --- | --- |
| `id` | 全局 `configId`，自增主键 |
| `scope` | `SYSTEM` / `USER`，只用于权限与展示，不参与身份定位 |
| `owner_user_id` | SYSTEM 固定为 `0`；USER 为真实用户 ID |
| `provider_id` | 关联 `llm_system_provider.id` |
| `provider_type` | 运行快照，Python 不回查厂商表推导 |
| `model_name` / `display_name` | 真实调用名与可选展示名 |
| `capability` | `CHAT` / `EMBEDDING` / `SPARSE_EMBEDDING` / `VISION` / `RERANK` / `ASR` |
| `protocol` / `api_base_url` | 非空运行快照；执行端按协议和能力选 adapter，不用厂商默认值兜底 |
| `api_key` | 正式加密器生成的密文；接口只返回脱敏值 |
| `is_active` | 是否允许按 `configId` 精确执行 |
| `snapshot_version` | 运行字段或 active 状态变化时递增，用于审计与缓存诊断 |

唯一键 `uk_llm_model_config_owner_model(scope, owner_user_id, provider_id, model_name, capability)` 防止同一所有者重复配置；索引 `idx_llm_model_config_owner_capability(scope, owner_user_id, capability, is_active)` 支撑可见配置与能力列表。

SYSTEM 配置仅管理员可写、对所有用户可执行；USER 配置仅所有者可写和执行。校验优先级固定为“不存在 → 已停用 → 越权 → 能力不匹配”。`scope`、`provider_type`、`model_name` 等均不是 ID 的组成部分，接口与 MQ 不再传 `source`、`presetId`、`userConfigId` 等别名。

### `llm_capability_default`

默认选择从可执行配置中拆出，避免 `is_default` 同时表达配置状态和用户偏好。

| 列 | 约束与语义 |
| --- | --- |
| `scope` | `SYSTEM` 平台兜底或 `USER` 用户覆盖 |
| `owner_user_id` | SYSTEM 固定 `0`；USER 为真实用户 ID |
| `capability` | 默认项所属能力 |
| `config_id` | 引用同 scope/owner、同 capability、active 的 `llm_model_config.id` |

唯一键 `uk_llm_capability_default_owner_cap(scope, owner_user_id, capability)` 保证每个所有者每种能力最多一个默认项。有效默认解析顺序是 USER 默认 → SYSTEM 默认。用户可以清除自己的默认覆盖；平台默认停用或删除前必须原子指定同能力替代项。默认关系不参与数据集已经固化的模型绑定。

### 厂商目录与运行快照

`llm_system_provider.default_protocol` 和厂商级 `api_base_url` 只作为管理端表单模板，不参与运行决策。`llm_provider_model` 以 `(provider_id, model_name, capability)` 为唯一目录事实；管理端可以从正式目录复制 `provider_type`、`model_name`、`display_name`、`capability`、`protocol`、`api_base_url` 到统一配置，也可以在一个事务内创建目录项并生成配置。Python 执行只读取 `llm_model_config` 快照。

外部模型同步任务和候选表只服务管理端审核流；候选发布后才进入正式目录。候选表与同步任务不要求 Python 运行端消费。

## 数据集模型绑定

`dataset_parse_config` 保留数据集级解析/检索职责，并直接引用统一 `configId`：

| 字段 | 能力 | 必需条件 | 是否可修改 |
| --- | --- | --- | --- |
| `sparse_embedding_config_id` | `SPARSE_EMBEDDING` | 创建数据集时必需 | 固化后不可改 |
| `dense_embedding_config_id` | `EMBEDDING` | 创建数据集时必需 | 固化后不可改 |
| `enhancement_chat_config_id` | `CHAT` | 表格增强或标题层级增强开启时必需 | 可替换/清空 |
| `enhancement_vision_config_id` | `VISION` | 图片增强开启时必需 | 可替换/清空 |
| `rerank_config_id` | `RERANK` | `recall.enable_rerank=true` 时必需 | 可替换/清空 |

五个字段都只存 `configId`，没有 `source` 列。每个字段有独立索引，便于配置停用/删除前检查引用。SYSTEM 配置可供任意用户数据集绑定；USER 配置必须属于数据集所有者。召回 session 签发与 Python 实际执行前都重新校验已存绑定的存在、active、归属和能力，禁止失效配置继续运行。

`chunking_config`、`enhancement_config`、`pdf_config`、`recall_config` 为 JSON；字段模型以 Python `src/core/dataset_config/models.py` 为准。`recall_config` 包含 `enable_rerank`，默认 `false`。唯一键 `uk_user_dataset(user_id, dataset_id)` 保证每个数据集一个配置行；`idx_dataset_parse_config_dataset(dataset_id)` 支撑按数据集读取。

## 对话与用量

- `chat_conversation.last_config_id`、`chat_message.config_id`、`llm_usage_log.config_id` 均引用全局 `llm_model_config.id`，不再附带来源字段。新 `chat_turn` 与 `usage_report` 消息要求非空 `config_id`。
- Chat 创建/切换只能使用精确 `CHAT` 配置；不能用“默认模型”或其它能力隐式替代。
- `chat_message` 一行代表一个 turn，以 `turn_id` 唯一并按 `GENERATING` → `COMPLETED` / `FAILED` upsert，终态不回退。
- `llm_usage_log` 保存 `stage`、`operation`、token、延迟和状态；全部模型调用（含 chat generate）统一经 `tolink.rag.usage_report` 落库，不保留对话级关联键。

## 共享表与删除语义

- Java/Python 共享表的生产结构只能通过 Python Alembic 演进；Java 本地脚本不可作为线上 migration 执行。
- `dataset`、`document_original_file`、`blog_post` 使用软删并令 `deleted_seq=id`，使死行退出活跃唯一键；`chat_conversation`、`chat_message` 物理删除。
- `document_original_file` 只保存上传事实。端到端解析终态权威源为 `document_parse_pipeline.pipeline_status`：`PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`。
- `document_parsed_log.retry_of_task_id` 指向上一轮；`document_parse_pipeline.superseded_by_task_id` 指向接班任务，均由 Python 写、Java 只读。
- `kb_document_chunk.chunk_id` 是业务唯一键，对应 `chat_message.references`；Java 仅按当前用户读取 active 且正文非空的记录。
- 文档上传配置不存 MySQL。部署默认来自 `tolink.document-file.*`，管理员覆盖保存在 Redis `runtime:document-file:upload-config`。
- `blog_post.content_object_key` 指向 PUBLIC OSS Markdown；正文不存 MySQL。`blog_asset` 保存封面与正文图片资源。
- `user_feedback` 不保存用户身份；`attachment_object_key` 只保存公开桶 object key，访问 URL 由 Java 拼装。

## 变更检查

涉及表、Entity 或 Mapper 的改动必须同时检查：

1. Python Alembic migration（生产唯一权威）；
2. `scripts/db/init.sql`（Java 本地 MySQL 镜像）；
3. `link-api/src/main/resources/schema.sql`（H2 镜像）；
4. Java/Python ORM 模型和索引名；
5. 本文档与相关 API、MQ、缓存契约。

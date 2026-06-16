# MySQL Schema

MySQL 建表脚本事实来源：`scripts/db/init.sql`；`scripts/db/schema.sql` 为初始化数据脚本。Entity 事实来源：`link-model/src/main/java/com/qingluo/link/model/dto/entity`。

## 表与 Entity

| 表 | Entity | 业务域 |
| --- | --- | --- |
| `sys_user` | `SysUser` | 用户、角色、状态 |
| `llm_system_provider` | `SystemProvider` | 系统 LLM 厂商（瘦身，去 `supported_models`/`config_schema`） |
| `llm_provider_model` | `ProviderModel` | 厂商→模型→能力目录（取代 `supported_models` JSON） |
| `llm_system_preset` | `SystemPreset` | 系统预设模板（自带加密平台 Key） |
| `llm_user_config` | `UserLLMConfig` | 用户 LLM 配置（下游唯一生效源；预设与自配统一汇入） |
| `llm_usage_log` | `UsageLog` | LLM 用量记录 |
| `chat_conversation` | `ChatConversation` | 对话 |
| `chat_message` | `ChatMessage` | 消息 |
| `dataset` | `Dataset` | 数据集 |
| `document_original_file` | `DocumentOriginalFile` | 原始文件 |
| `document_parse_file` | `DocumentParseFile` | 文件级解析聚合及最新任务指针 |
| `document_parsed_log` | `DocumentParsedLog` | Python 写入的解析（Markdown）产物日志；含重试链向前指针 `retry_of_task_id` |
| `document_parse_pipeline` | `DocumentParsePipeline` | Python 写入的后处理流水线（含稀疏向量阶段）终态 `pipeline_status` 与重试 CAS 列 `superseded_by_task_id`；Java 只读 |
| `blog_post` | `BlogPost` | 博客文章元数据、发布状态和 Markdown 对象指针 |
| `blog_asset` | `BlogAsset` | 博客封面资源和正文图片资源元数据 |
| `user_feedback` | `UserFeedback` | 匿名反馈、私有附件对象键、管理员处理状态与回复 |
| `dataset_parse_config` | `DatasetParseConfig`（LINK-149 待建） | 数据集级解析/检索参数配置（4 类 JSON：chunking / enhancement / pdf / recall）；跨端共享，Java 读写、Python 直读 |

## 约定

- 表结构变更必须同步 `scripts/db/init.sql`、本地运行时 `link-api/src/main/resources/schema.sql`、Entity 和本文档。
- MyBatis-Plus 逻辑删除字段遵循当前 `is_deleted` / `isDeleted` 映射。
- `llm_user_config.capability`（Entity `UserLLMConfig.capability`，单数）为专用能力标识，`VARCHAR(32) NOT NULL DEFAULT 'CHAT'`；合法取值以 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES` 为准：`CHAT` / `EMBEDDING` / `OCR` / `VISION` / `REASONING` / `CODE` / `TOOL_CALLING` / `RERANK`。列名以单数 `capability` 为准（曾误用复数 `capabilities`，已对齐线上库）。
- 厂商→模型→能力目录迁至 `llm_provider_model`（一个模型多能力=多行，唯一键 `uk_provider_model_cap (provider_id, model_name, capability)`），`llm_system_provider` 已去掉 `supported_models` / `config_schema` JSON。用户「配置厂商」即按该表展开整厂商 (模型, 能力) 写入 `llm_user_config`。
- `llm_system_preset`（自带加密平台 Key，唯一键 `uk_preset_provider_model_cap`）在用户注册时复制进 `llm_user_config`（`is_system_preset=true`、`is_default=true`），作为常备只读备选；预设已自带 `provider_type` / `protocol` / `api_base_url` 事实字段（见下「协议与入口三层语义」），注册镜像时直接平移这三列，不再 join 厂商表补全。
- `llm_user_config` 一条配置按 (模型, 能力) 展开为多行，唯一键为 `uk_user_provider_model_capability (user_id, provider_id, model_name, capability, is_system_preset)`（含 `is_system_preset`，使同 (厂商,模型,能力) 的平台预设行与用户自配行可并存）。`is_active` 兼表「模型启停」与「生效过滤」，`is_default` 表按能力生效（单用户单能力唯一），`is_system_preset` 标记只读预设行；`api_base_url`（原 `custom_api_base_url`）由展开时复制自模型能力层事实值（不再灌厂商默认，见下「协议与入口三层语义」）。已删字段：`provider_name` / `config_name` / `priority` / `timeout_ms` / `max_retries` / `stream_enabled` / `extra_config`。按能力切换查询由 `idx_user_provider_cap (user_id, provider_type, capability)` 支撑。

### 协议与入口字段（LLM 模型能力协议改造）

四张 LLM 表新增 `protocol` / `api_base_url` 系列列，把「调用协议（API 家族）」与「调用入口基地址」从厂商身份推导改为显式数据字段。事实来源为 `scripts/db/init.sql`，新增列如下（与 `link-api/src/main/resources/schema.sql` H2 同步）：

| 表 | 新增列 | 类型 | 约束（当前 DDL） | 语义 |
| --- | --- | --- | --- | --- |
| `llm_system_provider` | `default_protocol` | `VARCHAR(32)` | `NOT NULL DEFAULT 'openai'` | 厂商默认协议模板，仅用于管理端展示与新增模型能力时预填，**不参与运行决策** |
| `llm_provider_model` | `protocol` | `VARCHAR(32)` | 当前 nullable（服务层保证非空，回填后收紧 `NOT NULL`） | 事实来源：本 (模型,能力) 真实调用协议，下游按 `protocol + capability` 选 adapter |
| `llm_provider_model` | `api_base_url` | `VARCHAR(512)` | 当前 nullable（服务层保证非空，回填后收紧） | 事实来源：**完整端点 URL**（Python 直打、不拼后缀，如 `.../v1/chat/completions`）；`google` 例外存 base 到 `/v1beta`，见下「base 形态」 |
| `llm_system_preset` | `provider_type` | `VARCHAR(32)` | 当前 nullable | 厂商类型快照，下沉对齐用户配置，镜像免 join |
| `llm_system_preset` | `protocol` | `VARCHAR(32)` | 当前 nullable | 创建预设时复制自模型能力层 |
| `llm_system_preset` | `api_base_url` | `VARCHAR(512)` | 当前 nullable | 创建预设时复制自模型能力层 |
| `llm_user_config` | `protocol` | `VARCHAR(32)` | 当前 nullable | 运行快照：复制自模型能力层，下游按 `protocol + capability` 选 adapter，不再查厂商/模型表 |

> `llm_user_config.api_base_url` 为既有列，本次仅改写入来源（厂商默认 → 模型能力事实），不新增列。存量库迁移策略：先以 nullable 加列 → 运行 seed/import 回填重点厂商 → 再 `ALTER ... NOT NULL`，避免锁表失败；全新 init 已直接带这些列。Python 执行端已确认 `protocol` 视为必填、运行期对 NULL fail-fast，故回填清理后 `llm_provider_model` / `llm_system_preset` / `llm_user_config` 的 `protocol` 应收紧为 `NOT NULL`（DB 约束 + 执行端 fail-fast 双保险）。共享库 schema 演进由 Python Alembic 落地，本仓 `scripts/db` 仅本地/测试用。
>
> **base 形态（2026-06 对齐 Python PR #192）**：`api_base_url` 语义在两层不同——**厂商层** `llm_system_provider.api_base_url` 存「协议基地址」（仅作新增模型时表单预填模板，不参与运行）；**模型能力层 / 用户配置层** 存「完整端点 URL」（Python 直打、不再拼后缀）。完整 URL = 基地址 + `(protocol, capability)` 端点后缀，后缀知识在 Java seed 生成器（`scripts/import_ragflow_configs.py`），唯一例外 `google` 仍下发 base 到 `/v1beta`。详见 `docs/api/api_contracts.md`「LLM 协议与入口契约」的完整端点对照表。

**`protocol` 枚举（5 个，按 API 家族收敛，小写）**：`openai` / `anthropic` / `google` / `jina` / `dashscope`。合法取值以 `LLMProtocolServiceImpl.SUPPORTED_PROTOCOLS` 为准，大小写敏感（`OPENAI` 等大写视为非法）。`openai` 吃掉所有 OpenAI 兼容厂商；`dashscope` 仅承载千问 rerank / ASR；`jina` 承载 Jina rerank / embedding。非法值由服务层抛 `INVALID_PROTOCOL(10015/400)`，缺协议或缺入口抛 `MODEL_CONFIG_INCOMPLETE(10014/400)`。

**协议与入口三层语义**：同一份 `protocol` / `api_base_url` 在三张表里语义不同，分清才能避免「用厂商默认值跑线上」的隐患。

| 层 | 表 / 字段 | 语义 | `api_base_url` 形态 | 是否参与运行 |
| --- | --- | --- | --- | --- |
| 厂商层（默认模板） | `llm_system_provider.default_protocol` + `api_base_url` | 管理端展示、新增模型能力时表单预填的占位值 | 协议**基地址**（模板） | 否（绝不参与运行） |
| 模型能力层（事实来源） | `llm_provider_model.protocol` + `api_base_url` | 每个 (模型,能力) 的真实调用协议与入口，唯一权威 | **完整端点 URL**（`google` 例外存 base） | 间接（被复制下沉） |
| 用户配置层（运行快照） | `llm_user_config.protocol` + `api_base_url` | 用户启用厂商时复制自模型能力层的运行时快照，Python 直接消费 | **完整端点 URL**（随模型层复制） | 是 |

关键不变量：用户配置展开（`setupProvider`）与预设镜像时，`protocol` / `api_base_url` **只复制自模型能力层，绝不 fallback 到厂商默认值**；同一厂商不同能力可落不同协议（典型：千问 chat=`openai`、rerank=`dashscope`），属合法场景。`llm_system_preset` 作为「整套可用配置模板」与一条用户配置同构，自带 `provider_type` / `protocol` / `api_base_url`，由模型能力层复制而来，使其可直接平移生成用户配置；`is_default` / `is_system_preset` 等运行态标记不在对齐范围。
- Java 端和 Python RAG 端共享数据库时，字段语义必须在本文件或模块文档中明确。
- `document_original_file` 只保存上传事实，不保存解析状态或解析产物。
- `document_parse_file.latest_parse_task_id` 指向 `document_parsed_log.task_id`；Markdown 产物定位由 Python 写入 `document_parsed_log`。
- **端到端终态权威源 = `document_parse_pipeline.pipeline_status`（大写 `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`）。** `document_parsed_log` 已不含 `task_status` / `failure_reason`（Python migration 0007 移除）；Java 判定首次 / 重试 / 已成功以 `document_parsed_log.parsed_object_key` + `pipeline_status` 为准，运行中扫描与结果消费的终态判定亦改用 `pipeline_status`。
- 重试链双向：`document_parsed_log.retry_of_task_id`（本轮→上一轮）与 `document_parse_pipeline.superseded_by_task_id`（旧→新），均由 Python 写、Java 只读；`document_parse_pipeline` 不含 `retry_count` / `last_retry_at`。
- Java 不再消费 `tolink.rag.parse_result`；解析终态以库侧 `document_parse_pipeline.pipeline_status`（大写）为准。
- Document file upload config is resolved from Redis key `document:file-upload:config`, with `tolink.document-file.*` as the default fallback.
- `blog_post.slug + deleted_seq` 唯一：`slug` 由后端生成去掉连字符的 32 位小写 UUID；活跃文章 `deleted_seq=0`，软删时写自身 ID。
- `blog_post.content_object_key` 指向私有 OSS 的 `blog/{postId}/content/{uuid}.md`；Markdown 正文不存 MySQL。
- `blog_asset.object_key` / `public_url` 指向公开封面或正文图片对象；`asset_type` 支持 `COVER` 和 `CONTENT_IMAGE`。正文图片可由编辑器上传，也可由 Markdown 导入/保存流程自动写入 PUBLIC OSS，并记录 `blog_asset`。
- `user_feedback` 首版为纯匿名反馈，不保存 `user_id`、`contact`、`is_anonymous`、`is_deleted`、`is_resolved`。`attachment_object_key` 只保存私有 MinIO object key，不保存 URL 或 bucket。合法值：`type` = `BUG` / `FEATURE` / `EXPERIENCE` / `OTHER`，`status` = `PENDING` / `PROCESSING` / `RESOLVED` / `CLOSED`，`priority` = `1` 高 / `2` 中 / `3` 低。Java 本地 schema 与 Python migration 必须保持字段名、默认值、索引一致。
- `dataset_parse_config`（LINK-149）跨端共享：Java 端读写、Python 端直读，DDL 真值在 Python migration 0017，Java 本地 schema（`scripts/db/init.sql` 与 H2 运行时 `link-api/src/main/resources/schema.sql`）与之保持字段名、默认值、索引一致。唯一键 `uk_user_dataset (user_id, dataset_id)`，索引 `idx_dataset_parse_config_dataset (dataset_id)`。四个 JSON 列内字段以 Python `src/core/dataset_config/models.py` 的 Pydantic 模型为准：`chunking_config`（3 项：`heading_break_level` / `min_candidate_chunk_tokens` / `overlap_tokens`）、`enhancement_config`（2 项开关：`enable_table_enhancement` / `enable_image_enhancement`，不含模型名，增强模型统一取发起用户 CHAT/VISION 默认模型）、`pdf_config`（1 项：`pdf_parser_backend`）、`recall_config`（6 项：`recall_result_limit` / `recall_context_token_budget` / `sparse_top_k` / `sparse_score_threshold` / `dense_top_k` / `dense_score_threshold`）。`(user_id, dataset_id)` 无行时由 Python 降级系统默认。

## 删除语义（隐性删除）

删除「数据集 / 文档文件」采用隐性删除（软删保留原文件），不物理删 OSS 原文件对象：

- `dataset`、`document_original_file`：软删（实体 `@TableLogic is_deleted`），删除转 `UPDATE`、读自动过滤；新增判别列 `deleted_seq`（活行=0、软删时=自身 id）并纳入唯一键（`dataset` 的 `uk_dataset_user_name_seq`、`document_original_file` 的 `uk_dof_name_suffix_seq`），使软删死行退出唯一键“活名额”，支持删后同名重建 / 重传（删除走显式 `UPDATE set is_deleted=1, deleted_seq=id`，`@TableLogic` 仅保留读过滤）。
- `chat_conversation`、`chat_message`：一律物理删；`chat_conversation` 已移除 `is_deleted` / `@TableLogic`（索引 `idx_chat_conversation_user_active_list` 不再含 `is_deleted`）；因物理删不存在软删占名额问题，唯一键 `uk_conversation_user_dataset_title (user_id, dataset_id, title)` 无需 `deleted_seq`。
- `document_parse_file`、`document_parsed_log`：删除时 Java 端不再触碰，交 Python 随删除通知清理（MQ 占位、未实现）。
- 删除事务提交后（afterCommit）预留通知 Python 删除其侧衍生产物（OSS 清洗文件 / 向量等）的发送点（占位，不落 producer / topic / 消息体）。
- `blog_post`：软删并写 `deleted_seq=id`，不删除 Markdown 私有对象，也不批量删除图片对象。
- `blog_asset`：删除资源行使用软删；若资源是当前封面则清空 `blog_post.cover_asset_id`；正文图片仍被当前 Markdown 引用时拒绝删除；允许删除时同步删除 PUBLIC OSS 对象。

## 命名迁移（knowledge → document，B 组）

文档文件上传配置域的运行时键由 `knowledge*` 统一迁移为 `document*`。对存量环境的处理：

- **MySQL 表**：历史表 `knowledge_file_config` / `document_file_config` 已废弃并移除；存量环境可执行 `DROP TABLE IF EXISTS document_file_config;` 清理。
- **Redis key**：`knowledge:file-upload:config` → `document:file-upload:config`。旧 key 在迁移后成为孤儿，`DocumentFileConfigInitializer` 启动时仅以应用默认值补齐新 key（自愈），旧 key 可手动删除。
- **配置前缀 / 环境变量**：`tolink.knowledge-file.*` → `tolink.document-file.*`；环境变量 `KNOWLEDGE_FILE_*` → `DOCUMENT_FILE_*`，部署环境需同步更新（见 `.env.example`、`docs/ops/configuration.md`）。
- **Admin API**：`/api/v1/admin/knowledge-file-config` → `/api/v1/admin/document-file-config`（无兼容别名，调用方需切换）。

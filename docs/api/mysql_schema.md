# MySQL Schema

MySQL 建表脚本事实来源：`scripts/db/init.sql`；默认厂商与模型种子由 `scripts/db/seed_llm_providers.sql` 维护。Entity 事实来源：`link-model/src/main/java/com/qingluo/link/model/dto/entity`。

## 表与 Entity

| 表 | Entity | 业务域 |
| --- | --- | --- |
| `sys_user` | `SysUser` | 用户、角色、状态 |
| `llm_system_provider` | `SystemProvider` | 系统 LLM 厂商（瘦身，去 `supported_models`/`config_schema`） |
| `llm_provider_model` | `ProviderModel` | 厂商→模型→能力目录（取代 `supported_models` JSON） |
| `llm_provider_model_sync_job` | `ProviderModelSyncJob` | 外部模型目录刷新任务（Java 管理端内部候选流，不参与运行决策） |
| `llm_provider_model_sync_candidate` | `ProviderModelSyncCandidate` | 外部模型候选项（审核发布后才写入正式目录） |
| `llm_system_preset` | `SystemPreset` | LinkRag 系统兜底预设（自带加密平台 Key） |
| `llm_user_config` | `UserLLMConfig` | 用户自配 LLM 配置 |
| `llm_usage_log` | `UsageLog` | 全链路模型调用账本（含 `stage` / `operation`，瘦身后无对话级关联键；全部模型调用用量统一经 `usage_report` 通道写入） |
| `chat_conversation` | `ChatConversation` | 对话 |
| `chat_message` | `ChatMessage` | 对话消息（一行一轮：`query` + `answer` 同行；含 `turn_id`(幂等键，唯一索引) / `references`(JSON) / `request_id` / `status` / `error_code` / `error_message`） |
| `dataset` | `Dataset` | 数据集 |
| `document_original_file` | `DocumentOriginalFile` | 原始文件 |
| `document_parse_file` | `DocumentParseFile` | 文件级解析聚合及最新任务指针 |
| `document_parsed_log` | `DocumentParsedLog` | Python 写入的解析（Markdown）产物日志；含重试链向前指针 `retry_of_task_id` |
| `document_parse_pipeline` | `DocumentParsePipeline` | Python 写入的后处理流水线（含稀疏向量阶段）终态 `pipeline_status` 与重试 CAS 列 `superseded_by_task_id`；Java 只读 |
| `kb_document_chunk` | `KbDocumentChunk` | Python 写入的 Chunk 真值记录；Java 只读，用于历史消息按 `references` 批量恢复召回片段详情 |
| `blog_post` | `BlogPost` | 博客文章元数据、发布状态和 Markdown 对象指针 |
| `blog_asset` | `BlogAsset` | 博客封面资源和正文图片资源元数据 |
| `user_feedback` | `UserFeedback` | 匿名反馈、私有附件对象键、管理员处理状态与回复 |
| `dataset_parse_config` | `DatasetParseConfig` | 数据集级解析/检索参数配置（4 类 JSON：chunking / enhancement / pdf / recall）；跨端共享，Java 读写、Python 直读 |

## 约定

- 表结构变更必须同步 `scripts/db/init.sql`、本地运行时 `link-api/src/main/resources/schema.sql`、Entity 和本文档。
- `chat_message` 为「一行一轮」结构（对应 Python 仓库迁移 0021）：删 `role` / `token_count`，加 `query` / `answer`（原 `content` 改名）/ `references`(JSON，`JacksonTypeHandler`，列名为 MySQL 保留字需反引号包裹) / `request_id` / `status`。行数据由 Java 消费 `tolink.rag.chat_turn` 后落库（见 `docs/api/mq_contracts.md`），Python 不直接写本表；本仓不再保留独立历史迁移 SQL，当前结构已合入 `scripts/db/init.sql`。
- `chat_message`「后台续跑 + 可靠落库」（chat-stream-resilient-persist，列结构归 **Python migration 0023**，Java 只读写行）：加 `turn_id`(VARCHAR(64)，唯一索引 `uk_chat_message_turn_id`，历史行 NULL，唯一索引允许多 NULL) / `error_code`(VARCHAR(64) nullable) / `error_message`(VARCHAR(512) nullable)；`status` 复用既有 `VARCHAR(16)`，值语义由 `success/partial/failed` 改为 `GENERATING/COMPLETED/FAILED`（列结构不变，旧历史行保留 `success`）。Java 按 `turn_id` upsert：`GENERATING` 起点插「生成中」行、终态更新同行，状态不回退、按 `turn_id` 幂等。Java 不自行改共享库 DDL，本仓 `scripts/db/init.sql` 与 H2 schema 仅本地/测试用并与 0023 保持字段名、索引一致。
- `llm_usage_log` 升级为「全链路模型调用账本」（LINK-184）：增补 `stage`(VARCHAR(16) NOT NULL，`parse`/`recall`/`chat`) / `operation`(VARCHAR(16) NOT NULL，`embed`/`rerank`/`vision`/`table`/`generate`)，并放开 `config_id` 的 NOT NULL（系统配置调用如召回 query 编码落 NULL）。新增索引 `idx_usage_stage_operation (stage, operation)`。当前结构已合入 `scripts/db/init.sql`。
- `llm_usage_log` **瘦身**（LINK-191，对应 Python migration `0025_20260623_usage_log_slim`）：删 4 列 `fallback_config_id` / `conversation_id` / `message_id` / `request_id` 与 2 索引 `idx_conversation_id` / `idx_usage_message_id`。保留列：`id` / `user_id` / `config_id` / `provider_type` / `model_name` / `stage` / `operation` / `prompt_tokens` / `completion_tokens` / `total_tokens` / `latency_ms` / `status` / `error_message` / `created_at`；保留索引：`idx_user_date` / `idx_config_date` / `idx_usage_stage_operation`。自此**全部模型调用用量（含对话 generate）统一经 `tolink.rag.usage_report`（`UsageReportMQ` → `UsageReportPersistenceService`）写入**，`chat_turn` 通道不再写本表；账本不再保留对话级关联键，generate 行无法回溯到具体对话（有意为之）。当前结构已合入 `scripts/db/init.sql`，生产库升级以正式迁移链为准。
- MyBatis-Plus 逻辑删除字段遵循当前 `is_deleted` / `isDeleted` 映射。
- `llm_user_config.capability`（Entity `UserLLMConfig.capability`，单数）为专用能力标识，`VARCHAR(32) NOT NULL DEFAULT 'CHAT'`；合法取值以 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES` 为准：`CHAT` / `EMBEDDING` / `SPARSE_EMBEDDING` / `VISION` / `RERANK` / `ASR`。列名以单数 `capability` 为准（曾误用复数 `capabilities`，已对齐线上库）。`OCR` 已移除，不再作为独立模型能力；当前种子不再写入 OCR 行，历史清理由正式迁移链处理。
- 厂商→模型→能力目录迁至 `llm_provider_model`（一个模型多能力=多行，唯一键 `uk_provider_model_cap (provider_id, model_name, capability)`），`llm_system_provider` 已去掉 `supported_models` / `config_schema` JSON。用户「配置厂商」即按该表展开整厂商 (模型, 能力) 写入 `llm_user_config`。
- `llm_system_provider.icon_url` 保存厂商图标公开 OSS URL，`icon_object_key` 保存对应 MinIO/OSS object key（如 `providerIcon/{uuid}.png`）。前端新增厂商时先调用管理端图标上传接口拿 `iconUrl` 与 `iconObjectKey`，再一起写入厂商配置，避免厂商图标继续硬编码在前端。LinkRag 图标同样存本表，不在 `llm_system_preset` 中重复保存。
- 外部模型目录刷新（LINK-50）不污染正式目录：`llm_provider_model_sync_job` 记录每次刷新任务；`llm_provider_model_sync_candidate` 保存外部源候选、模型发布日期（`model_release_date`）、推断能力/协议/入口、模态与原始元数据。用户侧列表、用户配置展开、系统预设引用仍只读 `llm_provider_model`；候选必须由管理员审核发布后才进入正式目录。候选表用唯一键 `uk_sync_candidate_provider_source_model_cap (provider_id, sync_source, model_name, inferred_capability)` 收敛同一外部源下的同一模型能力，重复点击同步时更新既有候选的元数据与 `last_seen_at`，不新增重复记录。该能力只属于 Java 管理端，不要求 Python 端同步 schema 或消费候选表。
- `llm_system_provider.provider_type='linkrag'` 是系统服务厂商，供管理端维护 LinkRag 系统兜底预设；用户侧可添加厂商列表会过滤 LinkRag，用户 `setup-provider` 也拒绝配置该厂商，但配置列表会把 LinkRag 作为只读配置项返回。`scripts/db/seed_llm_providers.sql` 只创建 LinkRag 厂商基础行，不向 `llm_provider_model` 写入 LinkRag 模型能力；LinkRag 模型只写入 `llm_system_preset`，避免系统兜底模型进入用户自配厂商目录。
- 默认厂商与模型种子采用“主力厂商白名单 + 主推模型白名单 + 历史非白名单下架”：`scripts/db/seed_llm_providers.sql` 由本地 Docker MySQL 当前 `llm_system_provider` / `llm_provider_model` / `llm_system_preset` 定稿数据生成，保留 17 个国内/国外主力厂商与 LinkRag 系统厂商：`linkrag` / `openai` / `claude` / `gemini` / `deepseek` / `aliyun` / `glm` / `moonshot` / `xai` / `minimax` / `hunyuan` / `jina` / `volcengine` / `mimo` / `huggingface` / `openrouter` / `siliconflow`。厂商模型层每个厂商最多保留 5 个当前主推模型，合计 83 条模型能力记录，默认上架 83 条，且不包含 LinkRag 模型；其中阿里云只保留 Qwen 系列主推模型，OpenRouter 以多厂商优质模型为主，SiliconFlow 保留 DeepSeek/Kimi/BGE/Qwen 主力模型。LinkRag 系统预设独立维护 6 条默认兜底。运行种子时会把未列入白名单的历史厂商下架，把未列入当前种子的历史模型能力下架，并删除历史 LinkRag 厂商模型目录行，避免系统兜底模型出现在用户自配目录中。`model_name` 保持真实调用 ID，`display_name` 使用短展示名：保留主版本号（如 `2.5` / `4.8`），避免把发布日期、快照号或冗长版本标识暴露给前端。初始化 LLM 目录必须执行 `seed_llm_providers.sql`；全新环境首次写入 LinkRag 系统预设前需设置 `@linkrag_system_preset_api_key` 为 AES-256-GCM 加密后的平台 Key 密文，存量环境会优先复用已有预设密文。
- `llm_system_preset`（自带加密平台 Key，唯一键 `uk_preset_provider_model_cap`）已升级为 LinkRag 系统兜底预设，不再注册镜像到 `llm_user_config`。新增/更新系统预设时，目标恒为 `llm_system_provider.provider_type='linkrag'`；模型运行事实可以由管理员手动填写，也可以从正式 `llm_provider_model` 快捷复制，但不会把系统预设归属到源厂商。种子脚本会独立维护 LinkRag 系统默认预设，不依赖 LinkRag 的厂商模型目录；同一 `capability` 原则上只能有一条 `provider_type='linkrag' AND is_active=true AND is_default=true` 的系统默认预设，由 `SystemPresetServiceImpl` 或种子脚本清理同能力其他默认。有效配置解析命中系统兜底时，Java 返回 `source=SYSTEM` 与 `configId=llm_system_preset.id`，Python 按该引用读取本表。
- `llm_user_config` 一条配置按 (模型, 能力) 展开为多行，唯一键为 `uk_user_provider_model_capability (user_id, provider_id, model_name, capability, is_system_preset)`；新用户只写用户自配行（`is_system_preset=false`），`is_system_preset` 仅作历史兼容字段。`is_active` 兼表「模型启停」与「生效过滤」，`is_default` 表用户自配按能力默认（单用户单能力自配默认唯一）；`api_base_url`（原 `custom_api_base_url`）由展开时复制自模型能力层事实值（不再灌厂商默认，见下「协议与入口三层语义」）。已删字段：`provider_name` / `config_name` / `priority` / `timeout_ms` / `max_retries` / `stream_enabled` / `extra_config`。按能力切换查询由 `idx_user_provider_cap (user_id, provider_type, capability)` 支撑。用户无自配默认时，Java 回退读取 `llm_system_preset`。

### 协议与入口字段（LLM 模型能力协议改造）

四张 LLM 表新增 `protocol` / `api_base_url` 系列列，把「调用协议（API 家族）」与「调用入口基地址」从厂商身份推导改为显式数据字段。事实来源为 `scripts/db/init.sql`，新增列如下（与 `link-api/src/main/resources/schema.sql` H2 同步）：

| 表 | 新增列 | 类型 | 约束（当前 DDL） | 语义 |
| --- | --- | --- | --- | --- |
| `llm_system_provider` | `default_protocol` | `VARCHAR(32)` | `NOT NULL DEFAULT 'openai'` | 厂商默认协议模板，仅用于管理端展示与新增模型能力时预填，**不参与运行决策** |
| `llm_provider_model` | `protocol` | `VARCHAR(32)` | 当前 nullable（服务层保证非空，回填后收紧 `NOT NULL`） | 事实来源：本 (模型,能力) 真实调用协议，下游按 `protocol + capability` 选 adapter |
| `llm_provider_model` | `api_base_url` | `VARCHAR(512)` | 当前 nullable（服务层保证非空，回填后收紧） | 事实来源：**完整端点 URL**（Python 直打、不拼后缀，如 `.../v1/chat/completions`）；`google` 例外存 base 到 `/v1beta`，见下「base 形态」 |
| `llm_provider_model` | `display_name` | `VARCHAR(64)` | nullable | 模型展示名；真实调用仍使用 `model_name`，为空时展示层回退 `model_name` |
| `llm_system_preset` | `provider_type` | `VARCHAR(32)` | 当前 nullable | 厂商类型快照，系统兜底解析直接读取 |
| `llm_system_preset` | `protocol` | `VARCHAR(32)` | 当前 nullable | 创建预设时复制自模型能力层 |
| `llm_system_preset` | `api_base_url` | `VARCHAR(512)` | 当前 nullable | 创建预设时复制自模型能力层 |
| `llm_system_preset` | `is_default` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` | 是否为该能力的 LinkRag 系统兜底默认 |
| `llm_system_preset` | `display_name` | `VARCHAR(64)` | nullable | 系统兜底模型展示名；创建/重绑预设时复制自模型能力层 |
| `llm_user_config` | `protocol` | `VARCHAR(32)` | 当前 nullable | 运行快照：复制自模型能力层，下游按 `protocol + capability` 选 adapter，不再查厂商/模型表 |

> `llm_user_config.api_base_url` 为既有列，本次仅改写入来源（厂商默认 → 模型能力事实），不新增列。存量库迁移策略：先以 nullable 加列 → 运行 seed/import 回填重点厂商 → 再 `ALTER ... NOT NULL`，避免锁表失败；全新 init 已直接带这些列。Python 执行端已确认 `protocol` 视为必填、运行期对 NULL fail-fast，故回填清理后 `llm_provider_model` / `llm_system_preset` / `llm_user_config` 的 `protocol` 应收紧为 `NOT NULL`（DB 约束 + 执行端 fail-fast 双保险）。共享库 schema 演进由 Python Alembic 落地，本仓 `scripts/db` 仅本地/测试用。
>
> **base 形态（2026-06 对齐 Python PR #192）**：`api_base_url` 语义在两层不同——**厂商层** `llm_system_provider.api_base_url` 存「协议基地址」（仅作新增模型时表单预填模板，不参与运行）；**模型能力层 / 用户配置层** 存「完整端点 URL」（Python 直打、不再拼后缀）。完整 URL = 基地址 + `(protocol, capability)` 端点后缀，后缀知识在 Java seed 生成器（`scripts/import_ragflow_configs.py`），唯一例外 `google` 仍下发 base 到 `/v1beta`。详见 `docs/api/api_contracts.md`「LLM 协议与入口契约」的完整端点对照表。

**`protocol` 枚举（7 个，按执行端 adapter 收敛，小写）**：`openai` / `anthropic` / `google` / `jina` / `dashscope` / `bge_m3` / `doubao_vision`。合法取值以 `LLMProtocolServiceImpl.SUPPORTED_PROTOCOLS` 为准，大小写敏感（`OPENAI` 等大写视为非法）。`openai` 吃掉所有 OpenAI 兼容厂商；`dashscope` 承载千问 rerank / ASR；`jina` 承载 Jina rerank / embedding；`bge_m3` 与 `doubao_vision` 仅承载 `SPARSE_EMBEDDING`。非法值由服务层抛 `INVALID_PROTOCOL(10015/400)`，缺协议或缺入口抛 `MODEL_CONFIG_INCOMPLETE(10014/400)`。

**协议与入口三层语义**：同一份 `protocol` / `api_base_url` 在三张表里语义不同，分清才能避免「用厂商默认值跑线上」的隐患。

| 层 | 表 / 字段 | 语义 | `api_base_url` 形态 | 是否参与运行 |
| --- | --- | --- | --- | --- |
| 厂商层（默认模板） | `llm_system_provider.default_protocol` + `api_base_url` | 管理端展示、新增模型能力时表单预填的占位值 | 协议**基地址**（模板） | 否（绝不参与运行） |
| 模型能力层（事实来源） | `llm_provider_model.protocol` + `api_base_url` | 每个 (模型,能力) 的真实调用协议与入口，唯一权威 | **完整端点 URL**（`google` 例外存 base） | 间接（被复制下沉） |
| 系统预设层（LinkRag 兜底） | `llm_system_preset.provider_type` + `protocol` + `api_base_url` | 系统兜底配置，Java 在用户无自配默认时返回 `source=SYSTEM` | **完整端点 URL**（随模型层复制） | 是 |
| 用户配置层（运行快照） | `llm_user_config.protocol` + `api_base_url` | 用户启用厂商时复制自模型能力层的运行时快照，Java 优先返回 `source=USER` | **完整端点 URL**（随模型层复制） | 是 |

关键不变量：用户配置展开（`setupProvider`）与系统预设创建/重绑时，`protocol` / `api_base_url` **只复制自模型能力层，绝不 fallback 到厂商默认值**；系统预设的 `display_name` 也随模型能力层复制。真实调用始终使用 `model_name`，`display_name` 只供展示。同一厂商不同能力可落不同协议（典型：千问 chat=`openai`、rerank=`dashscope`），属合法场景。Java 有效配置解析顺序固定为 `llm_user_config` 用户自配默认 → `llm_system_preset` LinkRag 系统兜底默认，并通过 `source + configId` 消除两张表 ID 歧义。
- Java 端和 Python RAG 端共享数据库时，字段语义必须在本文件或模块文档中明确。
- `document_original_file` 只保存上传事实，不保存解析状态或解析产物。
- `document_parse_file.latest_parse_task_id` 指向 `document_parsed_log.task_id`；Markdown 产物定位由 Python 写入 `document_parsed_log`。
- **端到端终态权威源 = `document_parse_pipeline.pipeline_status`（大写 `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`）。** `document_parsed_log` 已不含 `task_status` / `failure_reason`（Python migration 0007 移除）；Java 判定首次 / 重试 / 已成功以 `document_parsed_log.parsed_object_key` + `pipeline_status` 为准，运行中扫描与结果消费的终态判定亦改用 `pipeline_status`。
- 重试链双向：`document_parsed_log.retry_of_task_id`（本轮→上一轮）与 `document_parse_pipeline.superseded_by_task_id`（旧→新），均由 Python 写、Java 只读；`document_parse_pipeline` 不含 `retry_count` / `last_retry_at`。
- Java 不再消费 `tolink.rag.parse_result`；解析终态以库侧 `document_parse_pipeline.pipeline_status`（大写）为准。
- `kb_document_chunk` 由 Python RAG 端写入和维护，`chunk_id` 为业务唯一键，对应 `chat_message.references` 中保存的 chunk id。Java 仅通过批量详情接口按当前用户读取 `lifecycle_status='ACTIVE'` 且正文非空的记录，并用 `doc_id` 关联 `document_original_file.id` 回填文件名；历史现查不含召回分数，`score` 返回 `null`。
- Document file upload config is resolved from Redis key `document:file-upload:config`, with `tolink.document-file.*` as the default fallback.
- `blog_post.slug + deleted_seq` 唯一：`slug` 由后端生成去掉连字符的 32 位小写 UUID；活跃文章 `deleted_seq=0`，软删时写自身 ID。
- `blog_post.content_object_key` 指向私有 OSS 的 `blog/{postId}/content/{uuid}.md`；Markdown 正文不存 MySQL。
- `blog_asset.object_key` / `public_url` 指向公开封面或正文图片对象；`asset_type` 支持 `COVER` 和 `CONTENT_IMAGE`。正文图片可由编辑器上传，也可由 Markdown 导入/保存流程自动写入 PUBLIC OSS，并记录 `blog_asset`。
- `user_feedback` 首版为纯匿名反馈，不保存 `user_id`、`contact`、`is_anonymous`、`is_deleted`、`is_resolved`。`attachment_object_key` 只保存私有 MinIO object key，不保存 URL 或 bucket。合法值：`type` = `BUG` / `FEATURE` / `EXPERIENCE` / `OTHER`，`status` = `PENDING` / `PROCESSING` / `RESOLVED` / `CLOSED`，`priority` = `1` 高 / `2` 中 / `3` 低。Java 本地 schema 与 Python migration 必须保持字段名、默认值、索引一致。
- `dataset_parse_config`（LINK-219）跨端共享：Java 端读写、Python 端直读，DDL 真值在 Python migration 0017/0030，Java 本地 schema（`scripts/db/init.sql` 与 H2 运行时 `link-api/src/main/resources/schema.sql`）与之保持字段名、默认值、索引一致。唯一键 `uk_user_dataset (user_id, dataset_id)`，索引 `idx_dataset_parse_config_dataset (dataset_id)`，向量模型绑定索引 `idx_dataset_parse_sparse_config (sparse_embedding_config_id)` / `idx_dataset_parse_dense_config (dense_embedding_config_id)`。`sparse_embedding_config_id` / `dense_embedding_config_id` 分别绑定当前用户启用中的 `llm_user_config.id`，能力必须为 `SPARSE_EMBEDDING` / `EMBEDDING`；创建数据集时必填并写入默认配置行，已有绑定不可通过解析配置 PUT 修改，历史行可为空但召回 session 签发会拒绝未补齐或已失效的绑定。四个 JSON 列内字段以 Python `src/core/dataset_config/models.py` 的 Pydantic 模型为准：`chunking_config`（7 项：`heading_break_level` / `min_candidate_chunk_tokens` / `overlap_tokens` / `max_chunk_tokens` / `hard_max_tokens` / `stage_two_algorithm` / `protected_neighbor_overlap`）、`enhancement_config`（3 项开关：`enable_table_enhancement` / `enable_image_enhancement` / `enable_heading_hierarchy`，不含模型名，增强模型统一取发起用户 CHAT/VISION 默认模型）、`pdf_config`（1 项：`pdf_parser_backend`）、`recall_config`（14 项：`recall_result_limit` / `recall_context_token_budget` / `bm25_top_k` / `sparse_top_k` / `sparse_score_threshold` / `dense_top_k` / `dense_score_threshold` / `recall_enabled_sources` / `recall_fusion_strategy` / `fusion_bm25_weight` / `fusion_sparse_weight` / `fusion_dense_weight` / `rerank_top_n` / `recall_strict`）。`recall_enabled_sources` 默认 `["bm25","sparse","dense"]`，`rerank_top_n` 默认 `8`，`recall_strict` 默认 `false`；旧 JSON 缺失这 3 项时 Java 读取回落默认。生产库升级以正式迁移链为准。

## 删除语义（隐性删除）

删除「数据集 / 文档文件」采用隐性删除（软删保留原文件），不物理删 OSS 原文件对象：

- `dataset`、`document_original_file`：软删（实体 `@TableLogic is_deleted`），删除转 `UPDATE`、读自动过滤；新增判别列 `deleted_seq`（活行=0、软删时=自身 id）并纳入唯一键（`dataset` 的 `uk_dataset_user_name_seq`、`document_original_file` 的 `uk_dof_name_suffix_seq`），使软删死行退出唯一键“活名额”，支持删后同名重建 / 重传（删除走显式 `UPDATE set is_deleted=1, deleted_seq=id`，`@TableLogic` 仅保留读过滤）。
- `chat_conversation`、`chat_message`：一律物理删；`chat_conversation` 已移除 `is_deleted` / `@TableLogic`（索引 `idx_chat_conversation_user_active_list` 不再含 `is_deleted`）。对话标题允许重复，不设置 `(user_id, dataset_id, title)` 唯一键。
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

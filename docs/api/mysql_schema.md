# MySQL Schema

MySQL 建表脚本事实来源：`scripts/db/init.sql`；`scripts/db/schema.sql` 为初始化数据脚本。Entity 事实来源：`link-model/src/main/java/com/qingluo/link/model/dto/entity`。

## 表与 Entity

| 表 | Entity | 业务域 |
| --- | --- | --- |
| `sys_user` | `SysUser` | 用户、角色、状态 |
| `llm_system_provider` | `SystemProvider` | 系统 LLM 厂商 |
| `llm_user_config` | `UserLLMConfig` | 用户 LLM API Key 配置 |
| `llm_usage_log` | `UsageLog` | LLM 用量记录 |
| `chat_conversation` | `ChatConversation` | 对话 |
| `chat_message` | `ChatMessage` | 消息 |
| `dataset` | `Dataset` | 数据集 |
| `document_original_file` | `DocumentOriginalFile` | 原始文件 |
| `document_parse_file` | `DocumentParseFile` | 文件级解析聚合及最新任务指针 |
| `document_parsed_log` | `DocumentParsedLog` | Python 写入的解析（Markdown）产物日志；含重试链向前指针 `retry_of_task_id` |
| `document_parse_pipeline` | `DocumentParsePipeline` | Python 写入的后处理流水线（含稀疏向量阶段）终态 `pipeline_status` 与重试 CAS 列 `superseded_by_task_id`；Java 只读 |

## 约定

- 表结构变更必须同步 `scripts/db/init.sql`、本地运行时 `link-api/src/main/resources/schema.sql`、Entity 和本文档。
- MyBatis-Plus 逻辑删除字段遵循当前 `is_deleted` / `isDeleted` 映射。
- `llm_user_config.capability`（Entity `UserLLMConfig.capability`，单数）为专用能力标识，`VARCHAR(32) NOT NULL DEFAULT 'CHAT'`；合法取值以 `LLMCapabilityServiceImpl.SUPPORTED_CAPABILITIES` 为准：`CHAT` / `EMBEDDING` / `OCR` / `VISION` / `REASONING` / `CODE` / `TOOL_CALLING` / `RERANK`（须与 `llm_system_provider.supported_models` 中的能力词汇表一致）。列名以单数 `capability` 为准（曾误用复数 `capabilities`，已对齐线上库）。
- `llm_user_config` 一条用户配置按模型支持的能力展开为多行（一个能力一行），唯一键为 `uk_user_provider_model_capability (user_id, provider_id, model_name, capability)`；默认配置按 `capability` 维度维护，并有 `idx_user_provider_cap (user_id, provider_type, capability)` 支撑按能力切换查询。
- Java 端和 Python RAG 端共享数据库时，字段语义必须在本文件或模块文档中明确。
- `document_original_file` 只保存上传事实，不保存解析状态或解析产物。
- `document_parse_file.latest_parse_task_id` 指向 `document_parsed_log.task_id`；Markdown 产物定位由 Python 写入 `document_parsed_log`。
- **端到端终态权威源 = `document_parse_pipeline.pipeline_status`（大写 `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`）。** `document_parsed_log` 已不含 `task_status` / `failure_reason`（Python migration 0007 移除）；Java 判定首次 / 重试 / 已成功以 `document_parsed_log.parsed_object_key` + `pipeline_status` 为准，运行中扫描与结果消费的终态判定亦改用 `pipeline_status`。
- 重试链双向：`document_parsed_log.retry_of_task_id`（本轮→上一轮）与 `document_parse_pipeline.superseded_by_task_id`（旧→新），均由 Python 写、Java 只读；`document_parse_pipeline` 不含 `retry_count` / `last_retry_at`。
- parse_result 消息体的 `task_status`（小写 `success` / `failed`）与库侧 `pipeline_status`（大写）是两套取值，禁止混用。
- Document file upload config is resolved from Redis key `document:file-upload:config`, with `tolink.document-file.*` as the default fallback.

## 删除语义（隐性删除）

删除「数据集 / 文档文件」采用隐性删除（软删保留原文件），不物理删 OSS 原文件对象：

- `dataset`、`document_original_file`：软删（实体 `@TableLogic is_deleted`），删除转 `UPDATE`、读自动过滤；新增判别列 `deleted_seq`（活行=0、软删时=自身 id）并纳入唯一键（`dataset` 的 `uk_dataset_user_name_seq`、`document_original_file` 的 `uk_dof_name_suffix_seq`），使软删死行退出唯一键“活名额”，支持删后同名重建 / 重传（删除走显式 `UPDATE set is_deleted=1, deleted_seq=id`，`@TableLogic` 仅保留读过滤）。
- `chat_conversation`、`chat_message`：一律物理删；`chat_conversation` 已移除 `is_deleted` / `@TableLogic`（索引 `idx_chat_conversation_user_active_list` 不再含 `is_deleted`）。
- `document_parse_file`、`document_parsed_log`：删除时 Java 端不再触碰，交 Python 随删除通知清理（MQ 占位、未实现）。
- 删除事务提交后（afterCommit）预留通知 Python 删除其侧衍生产物（OSS 清洗文件 / 向量等）的发送点（占位，不落 producer / topic / 消息体）。

## 命名迁移（knowledge → document，B 组）

文档文件上传配置域的运行时键由 `knowledge*` 统一迁移为 `document*`。对存量环境的处理：

- **MySQL 表**：历史表 `knowledge_file_config` / `document_file_config` 已废弃并移除；存量环境可执行 `DROP TABLE IF EXISTS document_file_config;` 清理。
- **Redis key**：`knowledge:file-upload:config` → `document:file-upload:config`。旧 key 在迁移后成为孤儿，`DocumentFileConfigInitializer` 启动时仅以应用默认值补齐新 key（自愈），旧 key 可手动删除。
- **配置前缀 / 环境变量**：`tolink.knowledge-file.*` → `tolink.document-file.*`；环境变量 `KNOWLEDGE_FILE_*` → `DOCUMENT_FILE_*`，部署环境需同步更新（见 `.env.example`、`docs/ops/configuration.md`）。
- **Admin API**：`/api/v1/admin/knowledge-file-config` → `/api/v1/admin/document-file-config`（无兼容别名，调用方需切换）。

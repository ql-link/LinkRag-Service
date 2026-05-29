# MySQL Schema

MySQL 建表脚本事实来源：`docs/db/init.sql`；`docs/db/schema.sql` 为初始化数据脚本。Entity 事实来源：`link-model/src/main/java/com/qingluo/link/model/dto/entity`。

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
| `document_parsed_log` | `DocumentParsedLog` | Python 写入的解析任务 / 产物日志 |

## 约定

- 表结构变更必须同步 `docs/db/init.sql`、本地运行时 `link-api/src/main/resources/schema.sql`、Entity 和本文档。
- MyBatis-Plus 逻辑删除字段遵循当前 `is_deleted` / `isDeleted` 映射。
- Java 端和 Python RAG 端共享数据库时，字段语义必须在本文件或模块文档中明确。
- `document_original_file` 只保存上传事实，不保存解析状态或解析产物。
- `document_parse_file.latest_parse_task_id` 指向 `document_parsed_log.task_id`；终态及 Markdown 产物定位由 Python 写入日志表。
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
- **配置前缀 / 环境变量**：`tolink.knowledge-file.*` → `tolink.document-file.*`；环境变量 `KNOWLEDGE_FILE_*` → `DOCUMENT_FILE_*`，部署环境需同步更新（见 `.env.example`、`docs/guides/configuration.md`）。
- **Admin API**：`/api/v1/admin/knowledge-file-config` → `/api/v1/admin/document-file-config`（无兼容别名，调用方需切换）。

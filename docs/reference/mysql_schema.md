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
| `document_original_file` | `KnowledgeOriginalFile` | 原始文件 |
| `document_parse_file` | `KnowledgeParseFile` | 文件级解析聚合及最新任务指针 |
| `document_parsed_log` | `KnowledgeParsedLog` | Python 写入的解析任务 / 产物日志 |
| `knowledge_file_config` | `KnowledgeFileConfig` | 知识文件运行配置 |

## 约定

- 表结构变更必须同步 `docs/db/init.sql`、本地运行时 `link-api/src/main/resources/schema.sql`、Entity 和本文档。
- MyBatis-Plus 逻辑删除字段遵循当前 `is_deleted` / `isDeleted` 映射。
- Java 端和 Python RAG 端共享数据库时，字段语义必须在本文件或模块文档中明确。
- `document_original_file` 只保存上传事实，不保存解析状态或解析产物。
- `document_parse_file.latest_parse_task_id` 指向 `document_parsed_log.task_id`；终态及 Markdown 产物定位由 Python 写入日志表。

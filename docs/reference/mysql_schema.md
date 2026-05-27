# MySQL Schema

数据库脚本事实来源：`docs/db/schema.sql`。Entity 事实来源：`link-model/src/main/java/com/qingluo/link/model/dto/entity`。

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
| `document_parsed_file` | `KnowledgeParsedFile` | 解析聚合文件 |
| `document_parse_log` | `KnowledgeParseTask` | 解析任务 / 解析日志 |
| `knowledge_file_config` | `KnowledgeFileConfig` | 知识文件运行配置 |

## 约定

- 表结构变更必须同步 `docs/db/schema.sql`、Entity 和本文档。
- MyBatis-Plus 逻辑删除字段遵循当前 `is_deleted` / `isDeleted` 映射。
- Java 端和 Python RAG 端共享数据库时，字段语义必须在本文件或模块文档中明确。

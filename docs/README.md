# Documentation — toLink-Service

按读者旅程组织。先想清楚"我是谁、要做什么"，再选目录。

## 我是谁

| 你的角色 | 看这里 |
| --- | --- |
| **对接方 / 业务方**：调 HTTP API、收 MQ 消息、读写共享数据库 | [api/](api/) |
| **内部开发者**：改代码、看模块边界、加新模块 | [internals/](internals/) |
| **运维 / 部署方**：起服务、调配置、看依赖 | [ops/](ops/) |
| **贡献者**：提 PR、跑测试、走 spec-as-test 流程 | [contributing.md](contributing.md) |
| **AI Agent / 新成员**：从 0 理解项目 | [../CLAUDE.md](../CLAUDE.md) |

## 目录速览

### [api/](api/) — 对外契约
| 文件 | 内容 |
| --- | --- |
| [api_contracts.md](api/api_contracts.md) | REST API 接口契约 |
| [mysql_schema.md](api/mysql_schema.md) | MySQL 表结构 |
| [mq_contracts.md](api/mq_contracts.md) | MQ 消息载荷与对接说明 |
| [error_codes.md](api/error_codes.md) | 业务错误码 |

### [internals/](internals/) — 内部实现
| 文件 | 内容 |
| --- | --- |
| [project_structure.md](internals/project_structure.md) | 项目结构与模块边界 |
| [cache_module.md](internals/cache_module.md) | Redis 缓存模块 |
| [mq_module.md](internals/mq_module.md) | MQ 组件架构 |
| [object_storage_module.md](internals/object_storage_module.md) | OSS / MinIO 对象存储 |
| [document_file_module.md](internals/document_file_module.md) | 知识文件 / 文档上传 |
| [llm_config_module.md](internals/llm_config_module.md) | LLM 厂商、模型能力、用户配置和系统预设 |
| [provider_model_sync_module.md](internals/provider_model_sync_module.md) | 外部模型目录同步、候选审核和发布 |
| [blog_module.md](internals/blog_module.md) | 博客草稿、Markdown 正文、PUBLIC OSS 图片资源 |
| [feedback_module.md](internals/feedback_module.md) | 用户反馈、公开附件和管理员处理 |
| [recall_session_module.md](internals/recall_session_module.md) | 召回 session JWT 签发和数据集授权范围 |
| [chat_module.md](internals/chat_module.md) | 对话生命周期和 chat_turn 幂等落库 |
| [usage_module.md](internals/usage_module.md) | usage_report 入库和用量聚合查询 |
| [admin_log_module.md](internals/admin_log_module.md) | Loki 查询代理、日志过滤和 trace_id 排障 |
| [testing.md](internals/testing.md) | 测试约定 |

### [ops/](ops/) — 启动与配置
| 文件 | 内容 |
| --- | --- |
| [configuration.md](ops/configuration.md) | 配置详解 |
| [deployment.md](ops/deployment.md) | 部署指南 |
| [integration.md](ops/integration.md) | 跨端联调 |

### 开发流程 → [contributing.md](contributing.md)
分支、提交、测试、文档同步、spec-as-test 等贡献者规范见 [contributing.md](contributing.md)，完整约定在 [../CLAUDE.md](../CLAUDE.md)。

### 数据库脚本 → [`../scripts/db/`](../scripts/db/)
仅保留 `init.sql`（本地/测试表结构初始化）与 `seed_llm_providers.sql`（默认厂商与模型种子）。

## 文档体系约定

- 每个事实**只在一处**正式描述，其他位置用链接引用。
- 文档是代码的摘要，代码是权威源。冲突时**改文档不改代码**。
- 临时交付物（brief / acceptance / technical_design / report）放 [.specs/](../.specs/)，本地工作产物、不进 repo（只 `.specs/README.md` 入库）。

# ToLink Service

ToLink Service 是 ToLink 的 Java 管理端后端服务，负责用户、LLM 配置、对话、数据集、知识文件、OSS 上传、解析任务投递和解析结果回传。实际文档解析、RAG 执行和 LLM 调用由 Python RAG 执行端承担。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 2.5.3 |
| 构建 | Maven 多模块 |
| ORM | MyBatis-Plus |
| 鉴权 | sa-token |
| 数据库 | MySQL 8 |
| 缓存 | Redis / Lettuce |
| MQ | Kafka / RabbitMQ 组件抽象 |
| 文件 | 本地存储 / MinIO OSS 组件 |
| 文档 | Spec-as-Test：brief + acceptance.feature + technical_design |

## 模块结构

```text
link-model       # Entity、Request/Response DTO、Enum、Result/PageResult
link-core        # 异常体系、全局异常处理、认证上下文、加密工具、基础配置
link-components  # Redis、MQ、OSS 组件
link-mapper      # MyBatis-Plus Mapper
link-service     # 核心业务服务
link-api         # Controller 与 Spring Boot 启动入口
```

主要启动类：

```text
link-api/src/main/java/com/qingluo/link/api/LinkApplication.java
```

## 核心能力

- 用户与权限：注册、登录、退出、用户资料、管理员用户管理，基于 sa-token 与 `ADMIN/USER` 角色。
- LLM 配置：系统厂商、用户 API Key 配置、默认配置、模型能力展示，API Key 使用 AES-256-GCM 加密。
- 对话与用量：会话、消息、用量汇总、日度统计、明细查询。
- 数据集与知识文件：数据集管理、原始文件上传、解析提交、解析状态查询、SSE 事件推送。
- OSS：本地存储和 MinIO 文件服务，区分 public/private 对象。
- MQ：解析任务 `tolink.rag.parse_task` 投递，解析结果 `tolink.rag.parse_result` 回传，缓存补偿 `tolink.cache.evict`。
- Redis：用户、LLM 配置、知识文件运行配置缓存，以及同步删除和补偿删除能力。

## 快速开始

### 1. 初始化数据库

```bash
mysql -h <DB_HOST> -u root -p < docs/db/schema.sql
mysql -h <DB_HOST> -u root -p tolink_rag_db < docs/db/init.sql
```

### 2. 配置环境变量

常用配置在 `link-api/src/main/resources/application.yml`：

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | Redis 连接 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 地址 |
| `TOLINK_MQ_VENDOR` | MQ 实现，默认 `kafka` |
| `OSS_SERVICE_TYPE` | OSS 实现，默认 `local` |
| `OSS_FILE_ROOT_PATH` | 本地 OSS 根目录 |
| `OSS_MINIO_*` | MinIO 配置 |
| `KNOWLEDGE_FILE_*` | 知识文件上传与内部访问配置 |
| `LLM_SECRET` | API Key 加密密钥，64 位十六进制字符串 |

### 3. 启动服务

```bash
mvn spring-boot:run -pl link-api
```

默认端口：`8080`。

### 4. 运行测试

```bash
mvn test
mvn -pl link-api test
mvn -pl link-service test
```

## API 概览

| 模块 | 入口 |
| --- | --- |
| Auth | `/api/v1/auth/login`、`/register`、`/logout` |
| User | `/api/v1/user/profile` |
| Admin | `/api/v1/admin/users`、`/providers`、`/knowledge-file-config` |
| Provider | `/api/v1/llm/providers` |
| LLM Config | `/api/v1/llm/configs` |
| Chat | `/api/v1/chat/conversations` |
| Usage | `/api/v1/llm/usage/*` |
| Dataset | `/api/v1/datasets` |
| Knowledge File | `/api/v1/datasets/{datasetId}/files`、`/api/v1/files/{fileId}` |
| OSS File | `/api/v1/oss-files/{bizType}` |
| Internal | `/api/v1/internal/files/{fileId}/content`、`/api/v1/internal/parse-tasks/{taskId}/events` |

完整契约见 `docs/reference/api_contracts.md`。

## AI 协作流程

本仓库使用 Spec-as-Test：

```text
brief.md -> acceptance.feature -> technical_design.md -> Code + Tests
```

入口文档为 `AGENTS.md` / `CLAUDE.md`，二者均指向 `.ai/prompts/project.md`。旧七阶段文档目录已移除，新需求统一使用 `docs/<需求名>/brief.md`、`acceptance.feature`、`technical_design.md`。

常用校验：

```bash
python3 scripts/setup_ai_links.py
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
```

## License

Private Project

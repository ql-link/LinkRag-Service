# ToLink Service

> ToLink 的 Java 管理端后端服务 · 当前版本 `v0.1.0`

ToLink Service 是 ToLink 的 Java 管理端后端服务，负责用户、LLM 配置、对话、数据集、文档文件、OSS 上传、解析任务投递和解析结果查询。实际文档解析、RAG 执行和 LLM 调用由 Python RAG 执行端承担。

## 系统边界

ToLink 采用「Java 管理端 + Python RAG 执行端」协作模式：

```text
            ┌──────────────────────┐        MySQL / Redis        ┌─────────────────────┐
  前端 ───▶ │  Java 管理端 (本服务)  │ ◀── OSS / MinIO ──────────▶ │  Python RAG 执行端   │
            │  入口·权限·配置·文件   │ ─── MQ (parse_task) ──────▶ │  文档解析·RAG·LLM    │
            │  状态查询·结果查询     │ ◀── Shared DB ───────────── │  解析产物·状态推进   │
            └──────────────────────┘     内部 HTTP (文件内容/召回)  └─────────────────────┘
```

- **Java 端**：管理入口、用户态资源、配置、文件上传、对象存储定位、解析任务投递、结果查询（前端轮询，不再 SSE 推送）。
- **Python 端**：文档解析、RAG 执行、LLM 调用、解析产物生成与部分状态推进。
- 两端通过 MySQL、MQ、OSS/MinIO 与必要的内部 HTTP 接口协作。

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
- 数据集与文档文件：数据集管理、原始文件上传、解析提交、解析状态查询（前端轮询 `parse-results`，不再 SSE 推送）。
- OSS：本地存储和 MinIO 文件服务，区分 public/private 对象。
- MQ：解析任务 `tolink.rag.parse_task` 投递，删除通知 `tolink.rag.document_delete` 投递，缓存补偿 `tolink.cache.evict`；Java 不再消费 `tolink.rag.parse_result`，解析终态以 Python 写入的共享数据库为准，由前端轮询 `parse-results` 查询。
- Redis：用户、LLM 配置、文档文件运行配置缓存，以及同步删除和补偿删除能力。
- 召回 session 签发：聊天召回走「前端直连 Python」，Java 校验登录态、用户状态、数据集权限后签发短期 HS256 session token（含 `streamUrl`），前端凭 token 直连 Python 拉召回/生成 SSE；Java 不在召回/生成请求路径上。
- 博客：文章草稿/发布管理、Markdown 正文与封面/正文图片对象存储、公开端查询。
- 反馈：匿名用户反馈提交（可选附件）与管理端反馈处理（状态、优先级、回复）。
- CDC 缓存补偿：消费 Canal binlog 经 CDC 桥接翻译为 `tolink.cache.evict`，对用户/配置/厂商等缓存目标做最终一致补偿失效。

## 快速开始

### 1. 初始化数据库

```bash
mysql -h <DB_HOST> -u root -p < scripts/db/schema.sql
mysql -h <DB_HOST> -u root -p tolink_rag_db < scripts/db/init.sql
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
| `DOCUMENT_FILE_*` | 文档文件上传与内部访问配置 |
| `LLM_SECRET` | API Key 加密密钥，64 位十六进制字符串 |

### 3. 启动服务

```bash
mvn spring-boot:run -pl link-api
```

默认端口：`8080`。

### 4. 运行测试

```bash
mvn clean test
mvn -pl link-api test
mvn -pl link-service test
```

## 容器化部署

仓库提供 `deploy/docker-compose.yml`，用于打包后的服务部署（数据库、Redis、MQ、OSS 等中间件按需自备或外接）：

```bash
cd deploy
docker compose up -d
```

服务通过环境变量对接外部 MySQL / Redis / Kafka / OSS，变量含义见下方「配置环境变量」。

## API 概览

| 模块 | 入口 |
| --- | --- |
| Auth | `/api/v1/auth/login`、`/register`、`/logout` |
| User | `/api/v1/user/profile` |
| Admin | `/api/v1/admin/users`、`/providers`、`/document-file-config` |
| Provider | `/api/v1/llm/providers` |
| LLM Config | `/api/v1/llm/configs` |
| Chat | `/api/v1/chat/conversations` |
| Usage | `/api/v1/llm/usage/*` |
| Dataset | `/api/v1/datasets` |
| Document File | `/api/v1/datasets/{datasetId}/files`、`/api/v1/files/{fileId}` |
| Recall | `/api/v1/recall/sessions`（签发前端直连 Python 召回的 session token） |
| Blog | `/api/v1/blog`（公开端）、`/api/v1/admin/blog`（管理端） |
| Feedback | `/api/v1/feedback`（提交）、`/api/v1/admin/feedback`（管理端处理） |
| OSS File | `/api/v1/oss-files/{bizType}` |
| Internal | `/api/v1/internal/files/{fileId}/content`、`/api/v1/internal/parse-tasks/{taskId}/events` |

完整契约见 `docs/api/api_contracts.md`。

## AI 协作流程

本仓库使用 Spec-as-Test：

```text
brief.md -> acceptance.feature -> technical_design.md -> Code + Tests
```

入口文档为 `AGENTS.md` / `CLAUDE.md`，二者均指向 `.ai/prompts/project.md`。旧七阶段文档目录已移除，新需求统一使用 `.specs/<需求名>/brief.md`、`acceptance.feature`、`technical_design.md`。

常用校验：

```bash
python3 scripts/setup_ai_links.py
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
```

## License

Private Project

# toLink-Service 配置指南

## 1. 概述

toLink-Service 采用 **分层配置架构**，将配置按职责清晰分离：

- **公共基础层**：环境无关的通用配置，所有环境共享
- **Profile 层**：环境相关的连接信息和运行参数，按 profile 隔离
- **环境变量层**：最高优先级，用于注入敏感值和差异化部署参数

核心设计理念：

1. **职责分离** — 公共配置与环境配置互不干扰
2. **安全加固** — 配置文件中不保留任何真实密码、IP 地址或密钥
3. **开发友好** — 克隆项目后零配置即可启动（local profile 使用 H2 内存数据库）
4. **部署统一** — `application-dev.yml` / `application-prod.yml` 使用同一组环境变量注入连接和敏感值
5. **命名规范** — 环境变量采用统一前缀命名，废弃历史别名

## 2. 配置文件清单与职责

| 文件 | 路径 | 职责 | 适用场景 |
|------|------|------|----------|
| `application.yml` | `link-api/src/main/resources/` | 环境无关的公共基础配置 | 所有环境始终加载 |
| `application-local.yml` | `link-api/src/main/resources/` | 本地开发配置（H2 + localhost Redis + local OSS + MQ none） | 本地开发，克隆即启动 |
| `application-dev.yml` | `link-api/src/main/resources/` | 开发服务器配置（环境变量引用，带开发默认容量） | 开发服务器 |
| `application-prod.yml` | `link-api/src/main/resources/` | 生产环境配置（环境变量引用，带生产默认容量） | 生产环境 |
| `schema.sql` | `link-api/src/main/resources/` | local profile 的 H2 初始化表结构 | 本地启动与 local profile 测试 |

### 各文件包含内容

| 文件 | 包含 | 不包含 |
|------|------|--------|
| `application.yml` | mybatis-plus 映射、sa-token、server.port、thread-pool 默认值、multipart 硬上限、文档上传默认值、业务缓存 TTL/容量、CDC 门禁、spring.application.name、logging.level | 数据源、Redis、Kafka、OSS 连接、敏感值、llm.api-key |
| `application-local.yml` | H2 内存数据库及 `classpath:schema.sql` 初始化、localhost Redis（无密码）、Kafka listener 禁用、MQ=none、OSS=local、固定测试密钥 | 真实服务器 IP、真实密码 |
| `application-dev.yml` / `application-prod.yml` | 所有连接通过 `${ENV_VAR}` 引用；连接池/线程池/日志通过 `${ENV_VAR:default}` 控制 | 真实密码、真实 IP、废弃别名 |

## 3. Profile 加载优先级

Spring Boot 配置加载遵循 **后加载覆盖先加载** 的原则：

```
┌─────────────────────────────────────────────────────────┐
│  优先级（从低到高）                                       │
├─────────────────────────────────────────────────────────┤
│  1. application.yml          ← 基础层，始终加载           │
│  2. application-{profile}.yml ← Profile 层，覆盖同名属性  │
│  3. 环境变量 / 系统属性        ← 最高优先级，覆盖所有文件   │
└─────────────────────────────────────────────────────────┘
```

### 覆盖机制说明

- `application.yml` 中定义了 `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`，默认激活 `local` profile
- 当 profile 为 `local` 时，`application-local.yml` 中的配置覆盖 `application.yml` 的同名属性
- 当 profile 为 `dev` / `prod` 时，对应 `application-dev.yml` / `application-prod.yml` 覆盖 `application.yml` 的同名属性
- 环境变量始终具有最高优先级，可覆盖任何文件中的配置值

### 示例

`application.yml` 中定义了 `thread-pool.document-upload.core-pool-size: 5`（线程池按池名嵌套，每业务一个专用池）。在 `application-dev.yml` 中引用为 `${THREAD_POOL_CORE_SIZE:5}` 时，如果环境变量 `THREAD_POOL_CORE_SIZE=10`，则最终生效值为 `10`。

## 4. 环境变量完整列表

### 4.1 系统配置

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `SPRING_PROFILES_ACTIVE` | 激活的 Spring profile | 是 | `local` | `dev` |

### 4.2 数据库（DB_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `DB_HOST` | MySQL 主机地址 | 是（dev） | 无 | `localhost` |
| `DB_PORT` | MySQL 端口 | 是（dev） | 无 | `3306` |
| `DB_NAME` | 数据库名称 | 是（dev） | 无 | `tolink_rag_db` |
| `DB_USERNAME` | 数据库用户名 | 是（dev） | 无 | `root` |
| `DB_PASSWORD` | 数据库密码 | 是（dev） | 无 | `your-db-password-here` |

### 4.3 连接池（DRUID_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `DRUID_INITIAL_SIZE` | Druid 初始连接数 | 否 | `0` | `5`（生产） |
| `DRUID_MIN_IDLE` | Druid 最小空闲连接 | 否 | `0` | `5`（生产） |
| `DRUID_MAX_ACTIVE` | Druid 最大活跃连接 | 否 | `8` | `30`（生产） |

### 4.4 Redis（REDIS_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `REDIS_HOST` | Redis 主机地址 | 是（dev） | 无 | `localhost` |
| `REDIS_PORT` | Redis 端口 | 是（dev） | 无 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 是（dev） | 无 | `your-redis-password-here` |
| `REDIS_DB` | Redis 数据库编号 | 否 | `0` | `0` |

### 4.5 Kafka（KAFKA_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 集群地址（逗号分隔） | 是（dev） | 无 | `localhost:9092` |
| `KAFKA_SECURITY_PROTOCOL` | Kafka 安全协议 | 否 | `SASL_PLAINTEXT` | `SASL_PLAINTEXT` |
| `KAFKA_SASL_MECHANISM` | SASL 认证机制 | 否 | `PLAIN` | `PLAIN` |
| `KAFKA_SASL_USERNAME` | Kafka SASL 用户名 | 是（dev） | 无 | `root` |
| `KAFKA_SASL_PASSWORD` | Kafka SASL 密码 | 是（dev） | 无 | `your-kafka-password-here` |
| `KAFKA_LISTENER_AUTO_STARTUP` | Kafka Listener 是否自动启动 | 否 | `true` | `true` |

### 4.6 MQ 组件

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `TOLINK_MQ_VENDER` | MQ 供应商类型（历史属性名为 `vender`） | 否 | `kafka` | `kafka` / `rabbitMQ` / `none` |
| `TOLINK_MQ_VENDOR` | `TOLINK_MQ_VENDER` 的兼容别名 | 否 | 空 | `kafka` |

### 4.7 MinIO（MINIO_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `MINIO_ENDPOINT` | MinIO 服务地址（host:port） | 是* | 无 | `localhost:9000` |
| `MINIO_ACCESS_KEY` | MinIO 访问密钥 | 是* | 无 | `your-minio-access-key-here` |
| `MINIO_SECRET_KEY` | MinIO 密钥 | 是* | 无 | `your-minio-secret-key-here` |
| `MINIO_RAW_BUCKET` | MinIO 原文件私有桶名（用户上传原文件，Java 写入，Python 只读） | 否 | `tolink-rag-raw` | `tolink-rag-raw` |
| `MINIO_PRIVATE_BUCKET` | MinIO 解析产物私有桶名（Python 写入 Markdown/图片，Java 只读） | 否 | `tolink-rag-docs` | `tolink-rag-docs` |
| `MINIO_PUBLIC_BUCKET` | MinIO 公开桶名（用户头像 + 博客图片/Markdown 正文 + 反馈附件，需配置匿名读） | 否 | `tolink-public` | `tolink-public` |

> \* MinIO 变量在 `OSS_SERVICE_TYPE=minio` 时必需
>
> 当前 MinIO 使用三桶：原文件私有桶 `tolink-rag-raw`、解析产物私有桶 `tolink-rag-docs`、公开桶 `tolink-public`。原博客专用桶 `tolink-blog` 已合并入公开桶，`MINIO_BLOG_BUCKET` 配置项废弃，部署侧待服务稳定后删除旧桶。
> 头像、厂商图标与反馈附件不新增独立桶配置，复用 `MINIO_PUBLIC_BUCKET` 公开桶。头像对象 key 格式为 `avatar/{userId}/{uuid}.{suffix}`，公开访问地址优先按 `tolink.oss.public-base-url` 生成并写入 `sys_user.avatar_url`；厂商图标对象 key 格式为 `providerIcon/{uuid}.{suffix}`，公开访问地址写入 `llm_system_provider.icon_url`，object key 写入 `llm_system_provider.icon_object_key`；反馈附件对象 key 由 Java 生成，格式为 `feedback/yyyy/MM/{uuid}.{suffix}`（精度到月），数据库只存 object key，可访问 URL 由后端按公开桶拼装。

### 4.8 阿里云 OSS（ALIYUN_OSS_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `ALIYUN_OSS_ENDPOINT` | 阿里云 OSS 端点 | 否 | 空 | `oss-cn-hangzhou.aliyuncs.com` |
| `ALIYUN_OSS_ACCESS_KEY_ID` | 阿里云 AccessKeyId | 否 | 空 | `your-access-key-id` |
| `ALIYUN_OSS_ACCESS_KEY_SECRET` | 阿里云 AccessKeySecret | 否 | 空 | `your-aliyun-secret-here` |
| `ALIYUN_OSS_PUBLIC_BUCKET` | 阿里云公开桶名 | 否 | 空 | `tolink-public` |
| `ALIYUN_OSS_PRIVATE_BUCKET` | 阿里云私有桶名 | 否 | 空 | `tolink-private` |

### 4.9 OSS 通用（OSS_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `OSS_SERVICE_TYPE` | OSS 服务类型 | 否 | `minio` | `local` / `minio` / `aliyun-oss` |

### 4.10 文档文件（DOCUMENT_FILE_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `DOCUMENT_FILE_INTERNAL_BASE_URL` | 内部服务访问地址 | 否 | `http://tolink-service:8080` | `http://localhost:8080` |
| `DOCUMENT_FILE_SERVICE_TOKEN` | 内部服务 Token | 否 | 空 | `your-service-token-here` |
| `TOLINK_DOCUMENT_FILE_MAX_SIZE_BYTES` | 单文件上传大小上限（字节） | 否 | `20971520` | `10485760` |
| `TOLINK_DOCUMENT_FILE_HARD_MAX_SIZE_BYTES` | 管理员动态配置允许的硬上限（字节） | 否 | `104857600` | `104857600` |
| `TOLINK_DOCUMENT_FILE_ALLOWED_SUFFIXES` | 允许上传的后缀列表（Spring Boot 集合绑定格式） | 否 | `md,markdown,pdf,docx,txt` | `pdf,md` |
| `DOCUMENT_FILE_HARD_MAX_SIZE` | Spring multipart 单文件硬上限 | 否 | `100MB` | `100MB` |
| `DOCUMENT_FILE_HARD_MAX_REQUEST_SIZE` | Spring multipart 请求硬上限 | 否 | `101MB` | `101MB` |
| `TOLINK_MARKDOWN_ASSETS_ENABLED` | Markdown 配套图片资源包开关 | 否 | `true` | `true` |
| `TOLINK_MARKDOWN_ASSET_MAX_BYTES` | 单张图片最大字节数 | 否 | `20971520` | `10485760` |
| `TOLINK_MARKDOWN_ASSET_MAX_COUNT` | 单资源包最大图片数 | 否 | `200` | `100` |
| `TOLINK_MARKDOWN_INVENTORY_MAX_COUNT` | 虚拟树目录清单最大条目数 | 否 | `5000` | `3000` |
| `TOLINK_MARKDOWN_BUNDLE_MAX_BYTES` | Markdown 与配套图片总字节上限 | 否 | `83886080` | `83886080` |
| `TOLINK_MARKDOWN_ASSET_PATH_MAX_LENGTH` | 图片相对路径最大字符数 | 否 | `512` | `512` |
| `TOLINK_MARKDOWN_DOCUMENT_PATH_MAX_LENGTH` | 文档相对路径最大字符数 | 否 | `255` | `255` |
| `TOLINK_ZIP_MAX_COMPRESSED_BYTES` | Web ZIP 压缩文件上限（capabilities 下发） | 否 | `104857600` | `104857600` |
| `TOLINK_ZIP_MAX_ENTRIES` | ZIP 最大条目数 | 否 | `5000` | `5000` |
| `TOLINK_ZIP_MAX_EXPANDED_BYTES` | ZIP 最大展开字节数 | 否 | `524288000` | `524288000` |
| `TOLINK_ZIP_MAX_RATIO` | ZIP 单条目最大压缩比 | 否 | `100` | `100` |
| `TOLINK_ZIP_MAX_DEPTH` | ZIP 最大目录深度 | 否 | `20` | `20` |

`DocumentFileProperties` 提供部署默认值和管理员可修改范围；管理员通过 `PUT /api/v1/admin/document-file-config` 把完整覆盖值写入 Redis `runtime:document-file:upload-config`，不设置 TTL。Redis key 缺失时使用部署默认值，故修改默认环境变量仍需重启所有实例。`DOCUMENT_FILE_HARD_MAX_SIZE`、网关 body 上限和反向代理上限必须不低于 `TOLINK_DOCUMENT_FILE_HARD_MAX_SIZE_BYTES`。

多实例默认值由无 TTL key `runtime:document-file:default-fingerprint` 校验。受控修改部署默认值时，应先完成所有实例配置收敛，再删除该 fingerprint key，让新版本实例重新建立指纹；滚动过程中指纹不一致会令动态 PUT 返回 503，避免不同实例接受不同后缀/大小边界。不要删除 `runtime:document-file:upload-config`，除非明确要撤销管理员覆盖并回到部署默认值。

### 4.11 LLM（LLM_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `LLM_SECRET` | API Key 加密密钥（64 位十六进制） | 是（dev） | 无 | `your-llm-secret-here` |

### 4.12 线程池（THREAD_POOL_*）

线程池按池名嵌套配置 `thread-pool.<池名>.*`（每业务一个专用池，互不共用）。`document-upload` 池（bean `documentUploadExecutor`）拒绝时把上传记录置 `failed`，不退回请求线程同步执行。以下环境变量映射到 `thread-pool.document-upload.*`：

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `THREAD_POOL_CORE_SIZE` | 核心线程数 | 否 | `5` | `10`（生产） |
| `THREAD_POOL_MAX_SIZE` | 最大线程数（须 ≥ 核心数，否则启动校验失败） | 否 | `10` | `20`（生产） |
| `THREAD_POOL_QUEUE_CAPACITY` | 队列大小 | 否 | `50` | `100`（生产） |
| `THREAD_POOL_KEEP_ALIVE_SECONDS` | 线程空闲存活秒数 | 否 | `60` | `60` |
| `THREAD_POOL_THREAD_NAME_PREFIX` | 线程名前缀 | 否 | `document-file-upload-` | `document-file-upload-` |

### 4.12.1 文档上传异步化（tolink.document-file.upload-async.*）

文档上传异步化的兜底配置（无对应 `THREAD_POOL_*` 环境变量，按需在 yml 覆盖）：

| 配置项 | 用途 | 默认值 |
|------|------|--------|
| `tolink.document-file.upload-async.temp-dir` | 上传临时文件目录（须与容器 multipart 临时目录同卷，使 `transferTo` 走 rename） | `${java.io.tmpdir}/tolink/document-upload` |
| `tolink.document-file.upload-async.stuck-threshold` | `uploading` 超时阈值，超过仍 uploading 即由扫描置 `failed` | `10m` |
| `tolink.document-file.upload-async.scan-interval-ms` | 超时扫描间隔（毫秒） | `60000` |

### 4.13 日志

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `LOG_LEVEL` | 应用日志级别（`application.yml` 已接 `${LOG_LEVEL:info}`，对 `com.qingluo.link` 生效） | 否 | `info` | `debug`（本地排查） |
| `SQL_LOG_LEVEL` | Mapper SQL 日志级别（SLF4J） | 否 | `info` | `debug`（排查）/ `info`（生产） |
| `LOG_PATH` | JSON Lines 日志输出目录（`logback-spring.xml` 按天文件夹滚动、保留 7 天；Docker 部署挂载到宿主） | 否 | `logs` | `/app/logs` |
| `OBSERVABILITY_LOKI_BASE_URL` | Java 管理端日志查询代理访问 Loki 的内网地址；不应暴露给前端或公网 | 否 | `http://localhost:3100` | `http://100.86.10.52:3100` |

**链路追踪（trace_id）**：`link-observability` 提供 `TraceIdFilter` / `TraceContext` / `MdcTaskDecorator` / `TraceHeaders`。`TraceIdFilter` 为每个 HTTP 请求建立 `trace_id` 写入 MDC（优先复用上游 `X-Trace-Id` 头，缺失或非法则新建，并回写响应头）；异步线程池经 `MdcTaskDecorator` 透传。MQ 生产侧由 `MQSend` 适配层把当前 MDC `trace_id` 写入 `X-Trace-Id` header；Kafka 消费入口读取 `X-Trace-Id` / `x-trace-id` / `trace_id` / `trace-id` 并恢复 MDC，缺失或非法时自建。日志输出为 JSON Lines，Java 顶层字段包括 `time` / `level` / `service` / `host` / `pid` / `trace_id` / `logger_name` / `message` / `exception`。

**日志采集提示**：Java Logback JSON 已将 `trace_id`、`service` 等字段放在顶层；Python 当前 Loguru `serialize=True` 文件日志字段在 `record.extra.trace_id`、`record.extra.service`、`record.extra.host` 等路径下。Fluent Bit / Promtail / Loki 采集配置需要分别按两端实际 JSON 路径做字段提取或映射。

**管理端日志查询代理**：`GET /api/v1/admin/logs` 和 `/api/v1/admin/logs/labels` 由 Java 后端访问 Loki `query_range` / label API 后统一返回前端。`service` / 普通 `level` 会尽量作为 Loki label 查询，`trace_id` / `keyword` 作为 LogQL 文本过滤；`ACCESS` / `AUDIT` 通过 `logger_name` 文本过滤。Python RAG 端必须设置 `LOG_SERVICE_NAME=tolink-rag`，否则日志会和 Java 的 `tolink-service` 混在同一服务名下，前端无法按服务筛选。

**访问日志**：`AccessLogFilter`（排在 `TraceIdFilter` 之后）统一为全部 HTTP 端点记录一行 `方法 路径 status=… cost=…ms userId=… ip=…`，用专用 logger 名 `ACCESS`（可单独调级，如 `logging.level.ACCESS=warn` 降噪）。在 `finally` 落日志，正常/异常均记录；userId 尽力获取（未登录记 `-`）；异步请求（SSE）只计建流耗时；Swagger/接口文档/静态资源路径跳过。

**审计日志**：`AuditLog`（专用 logger 名 `AUDIT`，统一 `action=` 前缀，复用 `trace_id`）为安全/高危动作留痕——登录成功/失败（`LOGIN_SUCCESS`/`LOGIN_FAIL`）、注册（`REGISTER`）、登出（`LOGOUT`）、改用户状态/角色（`USER_STATUS_CHANGE`/`USER_ROLE_CHANGE`，记操作人+目标+前后值）、配置厂商 Key（`LLM_PROVIDER_SETUP`）、删配置（`LLM_CONFIG_DELETE`）、厂商增删（`PROVIDER_CREATE`/`PROVIDER_DELETE`）。**仅记标识与结果，严禁记录明文密码、API Key、token**。可经 `logging.level.AUDIT` 单独调级或拆独立 appender。

### 4.14 召回 session 签发（RECALL_SESSION_*）

前端直连 Python 召回的 session token 签发配置，前缀 `tolink.recall`（`RecallProperties`）：

| 名称 | 用途 | 是否必需 | 默认值 |
|------|------|----------|--------|
| `RECALL_SESSION_JWT_SECRET` | 前端直连召回 session token 的 HS256 **独立密钥**（LINK-104；须与 Python `RECALL_SESSION_JWT_SECRET` 一致） | 是 | 空 |
| `RECALL_SESSION_JWT_EXP_SECONDS` | session token 有效期（秒），Python 强制校验 `exp` | 否 | `30` |
| `RECALL_SESSION_STREAM_BASE_URL` | 前端可见的 Python RAG 流式问答地址（公网/网关），用于拼接响应 `streamUrl = base + /api/v1/rag/stream`（LINK-138：Python 端点由 `/api/v1/recall/stream` 改名） | 否 | `http://localhost:8000` |

> `session-jwt-secret`（`RECALL_SESSION_JWT_SECRET`）由 `RecallExecutorConfig` 在**启动期强校验**：为空时直接 fail-fast，因此启用本服务必须配置一个非空 session 密钥。
>
> **变更（LINK-122）**：旧召回网关链路（Java 中转代理 `/api/v1/recall/stream` → Python 内部端点）已废弃移除，其专属配置
> `RAG_PYTHON_BASE_URL`、`RECALL_INTERNAL_JWT_SECRET`、`RECALL_JWT_EXP_SECONDS`、`RECALL_STREAM_TIMEOUT_MS`、
> `RECALL_EMITTER_TIMEOUT_BUFFER_MS`、`RECALL_CONNECT_TIMEOUT_MS`、`RECALL_READ_TIMEOUT_MS`、
> `RECALL_RATE_LIMIT_PER_MINUTE`、`RECALL_EXECUTOR_*` 一并删除，部署时可移除这些环境变量。

## 4.15 缓存一致性（tolink.cache-consistency.*）

缓存一致性由 `CacheConsistencyProperties` 绑定，前缀为 `tolink.cache-consistency`。

| 配置项 | 用途 | 默认值 | 备注 |
|------|------|--------|------|
| `tolink.cache-consistency.enabled` | 是否启用统一缓存一致性组件 | `true` | 关闭后主流程首删直接跳过 |
| `tolink.cache-consistency.sync-delete-required` | 兼容保留字段 | `true` | 仅保留配置绑定兼容，不再控制主流程首删失败是否抛错 |
| `tolink.cache-consistency.sync-delete-max-wait-ms` | 单次删缓存的总重试预算（毫秒） | `600` | 首删与补偿删共用 |
| `tolink.cache-consistency.sync-delete-retry-interval-ms` | 删缓存失败后的重试间隔（毫秒） | `100` | 首删与补偿删共用 |
| `tolink.cache-consistency.null-cache-ttl-seconds` | 空值缓存 TTL（秒） | `60` | 读保护使用 |
| `tolink.cache-consistency.ttl-jitter-seconds` | TTL 抖动上限（秒） | `300` | 读保护使用 |
| `tolink.cache-consistency.load-wait-ms` | 并发回源等待时间（毫秒） | `50` | 读保护使用 |
| `tolink.cache-consistency.load-lock-ttl-ms` | 跨实例回源锁 TTL（毫秒） | `5000` | 只协调回源，不承载业务数据 |
| `tolink.cache-consistency.fence-ttl-seconds` | 写入 fence TTL（秒） | `2592000` | 默认 30 天；失效时续期 |
| `tolink.cache-consistency.cdc.enabled` | 是否启用 CDC 桥接生产端 | `false` | 默认全环境关闭；线上接入 Canal 后置 `true` 开启 |
| `tolink.cache-consistency.cdc.mappings-enabled` | 是否启用声明式表映射 | `false` | 消费者和路由验证后再开启 |
| `tolink.cache-consistency.cdc.consumer-targets-ready` | 当前补偿消费者是否已识别全部新 target | `false` | 滚动发布门禁 |
| `tolink.cache-consistency.cdc.database` | 允许处理的 Canal database | `${DB_NAME:tolink_rag_db}` | 其它数据库事件忽略 |
| `tolink.cache-consistency.cdc.source-topic` | Canal 原始变更 topic | 无 | `cdc.enabled=true` 时必填，如 `tolink.canal.binlog` |
| `tolink.cache-consistency.cdc.group-id` | CDC 桥接消费者消费组 | `tolink-cdc-bridge` | — |

补充说明：

- 主流程第一次删缓存的时机：
  - 事务内写路径：事务提交后 `afterCommit` 执行
  - 无事务写路径：数据库写成功后立即执行
- 只要数据库写已经成功，第一次删缓存失败都不会再改变请求结果，而是记录日志并依赖 `tolink.cache.evict` 补偿链路最终收敛。
- CDC / MQ 驱动的第二次补偿删除仍保持强失败语义：删除失败时抛异常，由消费重试机制继续收敛。
- CDC bridge 与补偿消费者的永久失败/重试耗尽会发布到 `<原 topic>.DLT`。上线前必须创建 `${tolink.cache-consistency.cdc.source-topic}.DLT` 和 `tolink.cache.evict.DLT`；DLT 发送使用 broker 确认，失败时源消费继续报错。运维修复原因后把 DLT 原 payload 重发到对应源 topic。
- CDC 桥接生产端（`tolink.cache-consistency.cdc.*`）默认全环境关闭。上线应按“消费者识别新 target → bridge/mapping → 业务缓存”的顺序逐步开启。Canal 起始位点（首次从当前位点、不回放历史）属 Canal 容器侧运维配置，不在 Java 配置内。

### 4.15.1 业务缓存（tolink.business-cache.*）

| 配置项 | 用途 | 默认值 |
|------|------|--------|
| `tolink.business-cache.enabled` | 数据库镜像业务缓存总开关 | `true`；但还受 CDC readiness 门禁 |
| `tolink.business-cache.dataset-parse-config-ttl` | Java/Python 共享的数据集解析配置原始快照基础 TTL | `7d` |
| `tolink.business-cache.user-profile-ttl` | 用户资料基础 TTL | `1d` |
| `tolink.business-cache.blog-published-index-ttl` | 公开博客发布索引基础 TTL | `1d` |
| `tolink.business-cache.blog-published-index-capacity` | 固定发布索引最大条数 | `100` |

`application.yml` 当前把 `tolink.cache-consistency.enabled=false`、CDC/mapping/consumer readiness 全部设为 false，因此即使 `business-cache.enabled=true`，数据库镜像缓存仍不会启用。完成 Kafka/Canal 和补偿消费者部署后再逐项打开。上传运行时配置不受该业务缓存总开关控制。

Python 后续启用 `dataset_parse_config` 读缓存前，必须确认 Java `BusinessCacheHealthIndicator` 为 READY；共享 value 使用版本化原始快照，不能由部署配置改成 Java response 或 Python execution bundle。

### 4.15.2 LLM 运行配置缓存（tolink.llm-runtime-cache.*）

| 配置项 | 用途 | 默认值 |
|------|------|--------|
| `tolink.llm-runtime-cache.enabled` | 允许启用 Python `configId` 运行配置读缓存 | `false` |
| `tolink.llm-runtime-cache.cdc-mapping-enabled` | 确认 Canal 已订阅并映射 `llm_model_config` | `false` |
| `tolink.llm-runtime-cache.consumer-targets-ready` | 确认所有补偿消费者已识别 `llm_runtime_config` target | `false` |

三个开关必须同时开启，且统一缓存一致性组件、CDC database/source topic 与 bridge 均已就绪。该门禁不依赖 `tolink.business-cache.enabled`：只有门禁 READY 时，Java 才允许事务提交后首删和 `llm_model_config` CDC 映射发出新 target；Python 负责运行配置读取与回源。发布时先升级补偿消费者，再启用 CDC 映射，最后启用 Python 读缓存；`LlmRuntimeCacheHealthIndicator` 暴露具体未就绪原因。

## 5. 本地开发快速启动

### 前置条件

- JDK 17+
- Maven 3.8+
- Redis（本地运行，默认端口 6379，无密码）

### 启动步骤

```bash
# 1. 克隆项目
git clone <repository-url>
cd toLink-Service

# 2. 直接启动（默认使用 local profile）
mvn spring-boot:run -pl link-api
```

无需额外配置。`local` profile 默认激活，使用：
- H2 内存数据库，并从运行时资源 `schema.sql` 初始化当前本地表结构（无需安装 MySQL）
- localhost Redis（需本地运行 Redis）
- Kafka listener 禁用（无需安装 Kafka）
- 本地文件系统 OSS（无需 MinIO）
- MQ 组件禁用（vender=none）

### 验证启动成功

应用启动后访问 `http://localhost:8080`，日志中应显示：

```
The following profiles are active: local
```

## 6. 开发/生产环境部署

开发服务器使用 `application-dev.yml`，生产环境使用 `application-prod.yml`，两者都通过环境变量注入连接与敏感值。

### 部署步骤

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. 编辑 .env，填入实际值
vim .env

# 3. 设置 profile
# 开发服务器：SPRING_PROFILES_ACTIVE=dev
# 生产环境：SPRING_PROFILES_ACTIVE=prod

# 4. 加载环境变量并启动
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run -pl link-api
```

### 开发环境 vs 生产环境差异

开发环境和生产环境使用相同变量名，默认容量不同；生产可继续通过以下环境变量覆盖：

| 参数 | 开发环境（默认值） | 生产环境（建议值） |
|------|-------------------|-------------------|
| `DRUID_INITIAL_SIZE` | `0` | `5` |
| `DRUID_MIN_IDLE` | `0` | `5` |
| `DRUID_MAX_ACTIVE` | `8` | `30` |
| `THREAD_POOL_CORE_SIZE` | `5` | `10` |
| `THREAD_POOL_MAX_SIZE` | `10` | `20` |
| `THREAD_POOL_QUEUE_CAPACITY` | `50` | `100` |
| `THREAD_POOL_KEEP_ALIVE_SECONDS` | `60` | `60` |
| `THREAD_POOL_THREAD_NAME_PREFIX` | `document-file-upload-` | `document-file-upload-` |
| `LOG_LEVEL` | `debug` | `info` |
| `SQL_LOG_LEVEL` | `info`，排查时 `debug` | `info` / `warn` |

### Docker 部署示例

```bash
docker run -d \
  --env-file .env \
  -p 8080:8080 \
  tolink-service:latest
```

### docker-compose 示例

```yaml
services:
  tolink-service:
    image: tolink-service:latest
    env_file:
      - .env
    ports:
      - "8080:8080"
```

## 7. 废弃别名迁移

以下环境变量名称已废弃，请迁移到新名称。配置文件中不再提供向后兼容的 fallback 表达式。

| 旧名称（已废弃） | 新名称 | 迁移说明 |
|-----------------|--------|----------|
| `DB_USER` | `DB_USERNAME` | 统一使用 USERNAME 后缀 |
| `KAFKA_USERNAME` | `KAFKA_SASL_USERNAME` | 明确 SASL 语义 |
| `KAFKA_PASSWORD` | `KAFKA_SASL_PASSWORD` | 明确 SASL 语义 |
| `OSS_MINIO_ENDPOINT` | `MINIO_ENDPOINT` | 简化前缀 |
| `OSS_MINIO_ACCESS_KEY` | `MINIO_ACCESS_KEY` | 简化前缀 |
| `OSS_MINIO_SECRET_KEY` | `MINIO_SECRET_KEY` | 简化前缀 |
| `OSS_MINIO_RAW_BUCKET` | `MINIO_RAW_BUCKET` | 简化前缀 |
| `OSS_MINIO_PRIVATE_BUCKET` | `MINIO_PRIVATE_BUCKET` | 简化前缀 |
| `MINIO_BLOG_BUCKET` | `MINIO_PUBLIC_BUCKET` | 博客专用桶 `tolink-blog` 已并入公开桶 `tolink-public` |
| `OSS_ALIYUN_ENDPOINT` | `ALIYUN_OSS_ENDPOINT` | 统一阿里云前缀 |
| `OSS_ALIYUN_ACCESS_KEY_ID` | `ALIYUN_OSS_ACCESS_KEY_ID` | 统一阿里云前缀 |
| `OSS_ALIYUN_ACCESS_KEY_SECRET` | `ALIYUN_OSS_ACCESS_KEY_SECRET` | 统一阿里云前缀 |
| `OSS_ALIYUN_PUBLIC_BUCKET` | `ALIYUN_OSS_PUBLIC_BUCKET` | 统一阿里云前缀 |
| `OSS_ALIYUN_PRIVATE_BUCKET` | `ALIYUN_OSS_PRIVATE_BUCKET` | 统一阿里云前缀 |
| `API_KEY_ENCRYPTION_SECRET` | `LLM_SECRET` | 简化命名 |
| `MINIO_BUCKET_NAME` | `MINIO_PUBLIC_BUCKET` / `MINIO_PRIVATE_BUCKET` | 拆分为公开/私有桶 |

### 迁移操作

1. 检查现有 `.env` 文件或部署脚本中是否使用了旧名称
2. 将旧名称替换为对应的新名称
3. 重启服务验证配置生效

## 博客上传与权限配置

- 博客正文和图片不设置独立业务层大小上限，但仍受 `spring.servlet.multipart.max-file-size`、`spring.servlet.multipart.max-request-size`、网关、JVM 和 OSS 客户端限制。当前应用 multipart 硬上限默认 100MB/101MB。
- 博客封面图片、编辑器上传的正文图片以及 Markdown 正文自动抓取的正文图片使用公开桶 `tolink-public`（`OssSavePlaceEnum.PUBLIC` 枚举），部署时必须对该桶配置匿名读策略（`mc anonymous set download tolink-public`）或公开反向代理。原 `tolink-blog` 专用桶已合并，存量对象不迁移、旧桶待服务稳定后删除。
- Markdown 自动抓取远端正文图片时，单张图片业务上限为 10MB；仅允许 `http` / `https`、jpg/jpeg/png/gif/webp，拒绝 svg，并拦截 localhost、回环、私有网段、链路本地等地址。下载失败、超时、大小超限、类型不允许或安全校验失败时保留原 URL，不阻断导入/保存。
- `.md` 内本地相对路径图片不会随单文件上传进入后端，需要改成 `http` / `https`、`data:image/*;base64`，或在编辑器中粘贴/上传正文图片。
- `SaTokenAnnotationConfig` 已启用 MVC 注解拦截，`@SaCheckRole("ADMIN")` 会在请求进入 Controller 前执行；`StpInterfaceImpl` 同时兼容数字和字符串形式的 loginId。

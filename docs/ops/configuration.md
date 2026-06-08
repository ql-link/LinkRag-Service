# toLink-Service 配置指南

## 1. 概述

toLink-Service 采用 **三文件分层配置架构**，将配置按职责清晰分离：

- **公共基础层**：环境无关的通用配置，所有环境共享
- **Profile 层**：环境相关的连接信息和运行参数，按 profile 隔离
- **环境变量层**：最高优先级，用于注入敏感值和差异化部署参数

核心设计理念：

1. **职责分离** — 公共配置与环境配置互不干扰
2. **安全加固** — 配置文件中不保留任何真实密码、IP 地址或密钥
3. **开发友好** — 克隆项目后零配置即可启动（local profile 使用 H2 内存数据库）
4. **部署统一** — 同一份 `application-dev.yml` 通过环境变量差异化同时服务 dev 和 prod
5. **命名规范** — 环境变量采用统一前缀命名，废弃历史别名

## 2. 配置文件清单与职责

| 文件 | 路径 | 职责 | 适用场景 |
|------|------|------|----------|
| `application.yml` | `link-api/src/main/resources/` | 环境无关的公共基础配置 | 所有环境始终加载 |
| `application-local.yml` | `link-api/src/main/resources/` | 本地开发配置（H2 + localhost） | 本地开发，克隆即启动 |
| `application-dev.yml` | `link-api/src/main/resources/` | 部署环境配置（纯环境变量引用） | 开发服务器 / 生产环境 |
| `schema.sql` | `link-api/src/main/resources/` | local profile 的 H2 初始化表结构 | 本地启动与 local profile 测试 |

### 各文件包含内容

| 文件 | 包含 | 不包含 |
|------|------|--------|
| `application.yml` | mybatis-plus 映射、sa-token、server.port、thread-pool 默认值、multipart 限制、allowed-suffixes、spring.application.name、logging.level | 数据源、Redis、Kafka、OSS 连接、敏感值、llm.api-key |
| `application-local.yml` | H2 内存数据库及 `classpath:schema.sql` 初始化、localhost Redis（无密码）、Kafka listener 禁用、MQ=none、OSS=local、固定测试密钥 | 真实服务器 IP、真实密码 |
| `application-dev.yml` | 所有连接通过 `${ENV_VAR}` 引用；连接池/线程池/日志通过 `${ENV_VAR:default}` 控制 | 真实密码、真实 IP、废弃别名 |

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
- 当 profile 为 `dev` 时，`application-dev.yml` 中的配置覆盖 `application.yml` 的同名属性
- 环境变量始终具有最高优先级，可覆盖任何文件中的配置值

### 示例

`application.yml` 中定义了 `thread-pool.document-upload.core-pool-size: 5`（线程池按池名嵌套，多池就绪，本次仅 `document-upload` 池）。在 `application-dev.yml` 中引用为 `${THREAD_POOL_CORE_SIZE:5}`。如果环境变量 `THREAD_POOL_CORE_SIZE=10`，则最终生效值为 `10`。

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
| `TOLINK_MQ_VENDOR` | MQ 供应商类型 | 否 | `kafka` | `kafka` / `rabbitmq` / `none` |

### 4.7 MinIO（MINIO_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `MINIO_ENDPOINT` | MinIO 服务地址（host:port） | 是* | 无 | `localhost:9000` |
| `MINIO_ACCESS_KEY` | MinIO 访问密钥 | 是* | 无 | `your-minio-access-key-here` |
| `MINIO_SECRET_KEY` | MinIO 密钥 | 是* | 无 | `your-minio-secret-key-here` |
| `MINIO_PUBLIC_BUCKET` | MinIO 公开存储桶名 | 否 | `tolink-rag-docs` | `tolink-rag-docs` |
| `MINIO_PRIVATE_BUCKET` | MinIO 私有存储桶名 | 否 | `tolink-rag-docs` | `tolink-rag-docs` |

> \* MinIO 变量在 `OSS_SERVICE_TYPE=minio` 时必需

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
| `DOCUMENT_FILE_SSE_TIMEOUT_MS` | 文件解析 SSE 连接超时毫秒数 | 否 | `300000` | `300000` |

### 4.11 LLM（LLM_*）

| 名称 | 用途 | 是否必需 | 默认值 | 示例值 |
|------|------|----------|--------|--------|
| `LLM_SECRET` | API Key 加密密钥（64 位十六进制） | 是（dev） | 无 | `your-llm-secret-here` |

### 4.12 线程池（THREAD_POOL_*）

线程池按池名嵌套配置 `thread-pool.<池名>.*`（多池就绪：每业务一个专用池，互不共用）。本次仅 `document-upload` 池（bean `documentUploadExecutor`，拒绝策略 AbortPolicy——池满拒绝并把上传记录置 `failed`，不退回请求线程同步执行）。以下环境变量映射到 `thread-pool.document-upload.*`：

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
| `MYBATIS_LOG_IMPL` | MyBatis SQL 日志实现类 | 否 | `org.apache.ibatis.logging.stdout.StdOutImpl` | `org.apache.ibatis.logging.nologging.NoLoggingImpl`（生产） |
| `LOG_PATH` | 日志输出目录（`logback-spring.xml` 按天文件夹滚动、保留 7 天；Docker 部署挂载到宿主） | 否 | `logs` | `/app/logs` |

**链路追踪（traceId）**：`TraceIdFilter` 为每个 HTTP 请求建立 traceId 写入 MDC（优先复用上游 `X-Trace-Id` 头，缺失则新建，并回写响应头）；异步线程池经 `MdcTaskDecorator` 透传，MQ 消费者与定时扫描入口经 `TraceContext.startNew()` 自建。日志格式以 `[%X{traceId}]` 输出，全链路（含异步、MQ、定时任务）日志可按 traceId 串联。

**访问日志**：`AccessLogFilter`（排在 `TraceIdFilter` 之后）统一为全部 HTTP 端点记录一行 `方法 路径 status=… cost=…ms userId=… ip=…`，用专用 logger 名 `ACCESS`（可单独调级，如 `logging.level.ACCESS=warn` 降噪）。在 `finally` 落日志，正常/异常均记录；userId 尽力获取（未登录记 `-`）；异步请求（SSE）只计建流耗时；Swagger/接口文档/静态资源路径跳过。

**审计日志**：`AuditLog`（专用 logger 名 `AUDIT`，统一 `action=` 前缀，复用 traceId）为安全/高危动作留痕——登录成功/失败（`LOGIN_SUCCESS`/`LOGIN_FAIL`）、注册（`REGISTER`）、登出（`LOGOUT`）、改用户状态/角色（`USER_STATUS_CHANGE`/`USER_ROLE_CHANGE`，记操作人+目标+前后值）、配置厂商 Key（`LLM_PROVIDER_SETUP`）、删配置（`LLM_CONFIG_DELETE`）、厂商增删（`PROVIDER_CREATE`/`PROVIDER_DELETE`）。**仅记标识与结果，严禁记录明文密码、API Key、token**。可经 `logging.level.AUDIT` 单独调级或拆独立 appender。

### 4.14 召回 session 签发（RECALL_SESSION_*）

前端直连 Python 召回的 session token 签发配置，前缀 `tolink.recall`（`RecallProperties`）：

| 名称 | 用途 | 是否必需 | 默认值 |
|------|------|----------|--------|
| `RECALL_SESSION_JWT_SECRET` | 前端直连召回 session token 的 HS256 **独立密钥**（LINK-104；须与 Python `RECALL_SESSION_JWT_SECRET` 一致） | 是 | 空 |
| `RECALL_SESSION_JWT_EXP_SECONDS` | session token 有效期（秒），Python 强制校验 `exp` | 否 | `30` |
| `RECALL_SESSION_STREAM_BASE_URL` | 前端可见的 Python 召回地址（公网/网关），用于拼接响应 `streamUrl` | 否 | `http://localhost:8000` |

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

补充说明：

- 主流程第一次删缓存的时机：
  - 事务内写路径：事务提交后 `afterCommit` 执行
  - 无事务写路径：数据库写成功后立即执行
- 只要数据库写已经成功，第一次删缓存失败都不会再改变请求结果，而是记录日志并依赖 `tolink.cache.evict` 补偿链路最终收敛。
- CDC / MQ 驱动的第二次补偿删除仍保持强失败语义：删除失败时抛异常，由消费重试机制继续收敛。

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

开发服务器和生产环境共用 `application-dev.yml`，通过环境变量差异化配置。

### 部署步骤

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. 编辑 .env，填入实际值
vim .env

# 3. 设置 profile 为 dev
# 在 .env 中确保：SPRING_PROFILES_ACTIVE=dev

# 4. 加载环境变量并启动
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run -pl link-api
```

### 开发环境 vs 生产环境差异

开发环境和生产环境使用相同的 `application-dev.yml`，通过以下环境变量实现差异化：

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
| `MYBATIS_LOG_IMPL` | `StdOutImpl` | `NoLoggingImpl` |

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
| `OSS_MINIO_PUBLIC_BUCKET` | `MINIO_PUBLIC_BUCKET` | 简化前缀 |
| `OSS_MINIO_PRIVATE_BUCKET` | `MINIO_PRIVATE_BUCKET` | 简化前缀 |
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

## parse_result 卡住扫描配置

前缀 `tolink.parse-result.stuck`（`ParseResultStuckProperties`），由 `DocumentParseStuckScanner` 使用：

| 配置项 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `default-threshold` | `PARSE_RESULT_STUCK_THRESHOLD` | `5m` | 卡住判定阈值。文件上传解析为用户在线等待场景，超过即视为异常 |
| `scan-interval-ms` | `PARSE_RESULT_STUCK_SCAN_INTERVAL_MS` | `60000` | 扫描间隔（毫秒） |
| `dataset-thresholds` | —（仅 yml） | 空 | 按数据集覆盖阈值，如 `dataset-thresholds.{datasetId}: 20m`；未配置回落 `default-threshold` |

说明：调度由 `SchedulingConfig`（`@EnableScheduling`）开启。监控指标走 Micrometer（`spring-boot-starter-actuator`），本地 registry，不接外部平台；无 Micrometer 时降级为仅日志。退避重试参数（最多 3 次、1s→×2→上限 10s）固化在 `ParseResultKafkaConfig`。

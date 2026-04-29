# ToLink Service 项目总介绍

## 1. 文档定位

`project_info.md` 是 AI 进入本仓库后的项目现状入口，用于说明当前项目结构、已有能力、组件现状、重点文档与最近变更。

本文件记录动态现状，不记录流程铁律。流程铁律统一维护在 `AGENTS.md`。

## 2. 项目概览

- 项目名称：ToLink Service
- 项目形态：多模块 Maven 项目
- 主要语言：Java 17
- 核心框架：Spring Boot 2.5.3
- 当前定位：AI LLM 管理端与知识文件管理端，负责账户、配置、数据集、知识文件、OSS 与 MQ 协作等能力

## 3. 核心技术栈

| 类别 | 技术 | 说明 |
| --- | --- | --- |
| 框架 | Spring Boot 2.5.3 | 项目基础框架 |
| ORM | MyBatis-Plus 3.4.2 | Mapper 与数据访问能力 |
| 认证 | sa-token 1.39.0 | Header 模式，承载登录与角色鉴权 |
| 数据库 | MySQL 8 | 主数据库，数据库名 `tolink_rag_db` |
| 缓存 | Redis（Lettuce） | 用于缓存与部分认证相关能力 |
| 安全 | AES-256-GCM | 用于 LLM API Key 加密存储 |
| 构建 | Maven | 多模块构建与依赖管理 |

## 4. 模块地图

| 模块 | 职责 | 说明 |
| --- | --- | --- |
| `link-model` | 数据模型层 | 维护 Entity、Request/Response DTO、Enum |
| `link-core` | 核心通用层 | 维护异常、认证上下文、加密工具、线程池等 |
| `link-components` | 通用组件中台 | 包含 Redis、MQ、OSS 等复用组件 |
| `link-mapper` | 数据访问层 | 维护 MyBatis-Plus Mapper |
| `link-api` | API 入口层 | 提供 Controller、异常处理、Spring Boot 启动入口 |
| `link-service` | 业务逻辑层 | 提供用户、配置、数据集、文件、用量等核心服务 |

## 5. 模块依赖关系

```text
link-model ←── link-core ←── link-api ←── link-service
                 ↑              ↑
                 │               └──► link-mapper ←── link-model
                 │
link-components (toLink-components-redis, toLink-components-mq, toLink-components-oss)
```

补充说明：

- `link-api` 是 Spring Boot 启动模块
- `link-service` 负责业务逻辑，不直接承担启动职责
- `link-mapper` 负责持久化访问
- `link-components` 提供 Redis、MQ、OSS 等横向复用能力

### 5.1 系统协作模式

当前系统采用“Java 管理端 + Python 执行端”的协作模式。

- Java 端负责：
  - 用户交互入口
  - 配置管理
  - 数据集与知识文件元数据管理
  - 原始文件上传、对象存储定位与异步任务触发
- Python 端负责：
  - 文档解析
  - RAG 执行
  - LLM 调用
  - 部分执行结果回写

两端通过以下方式协作：

- MySQL：共享结构化业务数据
- Redis：共享高频缓存数据
- MQ：异步任务投递与结果回传
- OSS / MinIO：共享文件对象与中间产物

### 5.2 全局职责边界

- ToLink Service 是 Java 管理端，不承担实际 LLM 调用和 RAG 生成执行
- Python 执行端不负责前端管理入口、用户态管理和首次文件上传入口
- 结构化状态、归属关系、列表查询优先由 MySQL 承担
- 文件对象本体和解析产物优先由对象存储承担
- 高读频、可回源的数据优先考虑 Redis 缓存

## 6. 通用能力地图

### 6.1 能力总览

| 通用能力 | 负责的功能 | 当前服务的业务域 | 关键入口 |
| --- | --- | --- | --- |
| Redis 缓存能力 | 缓存读优化、缓存失效、双删一致性 | `user`、`llm-config` | `DoubleDeleteCacheService`、`UserCacheService` |
| OSS 文件存储能力 | 文件上传、对象存储、公私有访问、私有文件本地解析 | `storage` | `IOssService`、`PrivateFileResolver`、`OssApplicationService` |
| MQ 异步协作能力 | 异步任务投递、结果回传、多厂商 MQ 适配 | `mq`、`storage` | `MQSend`、`KnowledgeParseTaskMQ` |
| 鉴权与用户上下文能力 | 登录校验、角色鉴权、当前用户识别 | `user`、`llm-config`、`chat`、`dataset`、`storage` | `sa-token`、`AuthContext`、`StpInterfaceImpl` |
| API Key 加密能力 | 敏感密钥加解密与脱敏展示 | `llm-config` | `ApiKeyEncryptService` |

### 6.2 能力详细说明

#### Redis 缓存能力

- 负责内容：
  - 高读频数据的缓存读优化
  - 配置与用户信息相关缓存失效
  - 双删缓存一致性保障
- 当前服务的业务域：
  - `user`
  - `llm-config`
- 当前能力：
  - 提供 `RedisTemplate` 与 `RedisUtils`
  - 提供 `DoubleDeleteCacheService`
  - 已落地用户信息缓存与配置类缓存失效能力
- 关键入口：
  - `link-components/toLink-components-redis`
  - `DoubleDeleteCacheService`
  - `UserCacheService` / `UserCacheServiceImpl`
- 什么时候优先复用：
  - 读多写少、允许 DB 回源的数据
  - 需要缓存失效或双删的配置类数据

#### OSS 文件存储能力

- 负责内容：
  - 文件对象上传、下载、删除
  - 公私有文件边界处理
  - 私有文件落地缓存与本地解析
  - 本地存储与 MinIO 切换
- 当前服务的业务域：
  - `storage`
- 当前能力：
  - `IOssService` 抽象
  - `LocalFileService` / `MinioFileService`
  - `PrivateFileResolver`
  - 通用 OSS 上传应用层 `OssApplicationService`
- 关键入口：
  - `link-components/toLink-components-oss`
  - `OssApplicationService`
  - `KnowledgeFileService`
- 什么时候优先复用：
  - 文件上传、对象 key 设计、公私有访问控制
  - 私有对象读取、本地缓存访问、对象删除

#### MQ 异步协作能力

- 负责内容：
  - 异步任务下发
  - 外部处理结果回传
  - Kafka / RabbitMQ 统一抽象
  - MQ 模型扫描与自动装配
- 当前服务的业务域：
  - `mq`
  - `storage`
- 当前能力：
  - `AbstractMQ`、`MQSend`、`MQMsgReceiver`
  - Kafka / RabbitMQ 适配
  - 已落地解析任务投递链路
  - 旧解析结果消费链路保留但默认关闭，二期解析结果由 Python 端直接写库并通过 Java 回调接口向前端转发进度事件
- 关键入口：
  - `link-components/toLink-components-mq`
  - `KnowledgeParseTaskMQ`
  - `KnowledgeParseTaskService`
  - `KnowledgeParseSseService`
  - `KnowledgeParseResultMQ`（旧链路，默认关闭）
- 什么时候优先复用：
  - 跨模块异步协作
  - 需要解耦、削峰或外部系统回传

#### 鉴权与用户上下文能力

- 负责内容：
  - 登录状态校验
  - 当前登录用户识别
  - 角色查询与角色鉴权
- 当前服务的业务域：
  - `user`
  - `llm-config`
  - `chat`
  - `dataset`
  - `storage`
- 当前能力：
  - 基于 sa-token 的登录态管理
  - `AuthContext` 提供当前用户 ID 获取
  - `StpInterfaceImpl` 提供角色加载
- 关键入口：
  - `AuthContext`
  - `StpInterfaceImpl`
  - Controller 中的 `@SaCheckLogin`
- 什么时候优先复用：
  - 任何用户态接口
  - 需要根据当前登录用户过滤资源归属
  - 需要管理员角色校验

#### API Key 加密能力

- 负责内容：
  - API Key 加密存储
  - API Key 解密读取
  - API Key 脱敏展示
- 当前服务的业务域：
  - `llm-config`
- 当前能力：
  - AES-256-GCM 加密解密
  - 基于 `LLM_SECRET` 的密钥注入
  - 脱敏方法 `maskApiKey`
- 关键入口：
  - `ApiKeyEncryptService`
- 什么时候优先复用：
  - 新增敏感密钥类配置字段
  - 需要展示脱敏后的密钥摘要

## 7. 当前主要业务域

### 7.1 业务域总览

| 业务域 | 负责的业务 | 当前能力 | 依赖的中间件/能力 |
| --- | --- | --- | --- |
| `user` | 用户身份与后台用户管理 | 注册、登录、个人信息、后台用户管理 | MySQL、Redis、sa-token |
| `llm-config` | 厂商配置与用户模型配置 | 系统厂商管理、用户级 LLM 配置、默认配置切换 | MySQL、Redis、API Key 加密 |
| `chat` | 对话与消息历史 | 对话创建、列表查询、消息历史查询、删除 | MySQL |
| `dataset` | 数据集管理 | 数据集创建、列表、详情、删除 | MySQL |
| `storage` | 文件上传与对象存储协同 | 原始知识文件上传、文件记录管理、OSS 上传下载、公私有文件访问 | MySQL、OSS、MQ |
| `mq` | 异步解析协作 | 解析任务投递、解析结果回传、结果入库 | MQ、MySQL |

### 7.2 业务域详细说明

#### `user`

- 负责内容：
  - 用户注册、登录、登出
  - 当前登录用户信息查询与维护
  - 管理员对用户状态、角色的管理
- 当前能力：
  - 基于 sa-token 的登录态管理
  - 基于 `AuthContext` 的用户上下文获取
  - 用户信息缓存与角色鉴权辅助
- 关键入口：
  - `AuthController`
  - `UserController`
  - `AdminController`
  - `AuthService`
  - `AdminUserService`
- 关联中间件/能力：
  - MySQL：用户主数据
  - Redis：`user:info:{userId}`、`user:role:{userId}`
  - sa-token：登录与角色校验

#### `llm-config`

- 负责内容：
  - 系统级厂商维护
  - 用户级 API Key / 模型配置维护
  - 默认配置切换与配置启停
- 当前能力：
  - 系统厂商配置查询与维护
  - 用户级模型配置增删改查
  - API Key 加密存储
  - 配置变更后的缓存失效
- 关键入口：
  - `ConfigController`
  - `AdminController`
  - `UserLLMConfigService`
  - `SystemProviderService`
  - `AdminProviderService`
- 关联中间件/能力：
  - MySQL：`llm_system_provider`、`llm_user_config`
  - Redis：`llm:cfg:{configId}`、`llm:u_def:{userId}`、`llm:pvd:{providerType}`
  - `ApiKeyEncryptService`

#### `chat`

- 负责内容：
  - 对话创建
  - 对话列表与消息历史查询
  - 对话删除
- 当前能力：
  - 以对话为主线维护消息历史
  - 记录最后使用的配置与模型快照
- 关键入口：
  - `ChatController`
  - `ChatService`
- 关联中间件/能力：
  - MySQL：`chat_conversation`、`chat_message`

#### `dataset`

- 负责内容：
  - 数据集创建、分页查询、详情、删除
  - 为知识文件归属与后续解析提供业务边界
- 当前能力：
  - 以用户为边界维护数据集
  - 为知识文件上传与检索提供上层归属
- 关键入口：
  - `DatasetController`
  - `DatasetService`
- 关联中间件/能力：
  - MySQL：`dataset`

#### `storage`

- 负责内容：
  - 原始知识文件上传
  - 知识文件记录查询、详情、删除
  - 统一 OSS 文件上传与访问
  - 私有文件下载与本地缓存访问
- 当前能力：
  - 原始知识文件落 OSS / MinIO，并在 DB 中记录元数据
  - 支持按数据集管理知识文件，原文件唯一性按 `dataset_id + user_id + original_filename + file_suffix` 控制
  - 原文件上传状态收敛为 `uploading`、`success`、`failed`
  - 上传失败记录允许重试并复用原 `object_key`，上传中记录超过 1 分钟可补偿为失败
  - 原文件上传通过 `knowledgeFileUploadExecutor` 线程池执行，避免业务代码直接创建上传线程
  - 上传成功后可按开关自动创建解析任务并投递 MQ，手动解析也会创建独立解析任务
  - 解析任务记录同一原文件的多次解析历史，状态收敛为 `created`、`processing`、`success`、`failed`
  - 解析产物表只保存每个原文件最新成功 Markdown 产物，成功解析次数在解析成功后更新
  - 前端可通过 SSE 接收 Python 回调到 Java 的解析进度事件，并可按文件列表查询解析结果汇总
  - 支持私有文件通过 `PrivateFileResolver` 访问
  - 支持通用 OSS 上传入口
- 关键入口：
  - `KnowledgeFileController`
  - `InternalKnowledgeFileController`
  - `OssFileController`
  - `ApiLocalOssPreviewController`
  - `KnowledgeFileService`
  - `KnowledgeParseTaskService`
  - `KnowledgeParseSseService`
  - `KnowledgeFileUploadExecutorConfig`
  - `OssApplicationService`
- 关联中间件/能力：
  - MySQL：`document_original_file`、`document_parse_log`、`document_parsed_file`
  - OSS：原文件 bucket 为 `rag-raw`，Markdown 解析产物 bucket 为 `rag-md`
  - MQ：上传后自动解析或手动解析触发 `tolink.rag.parse_task` 投递

#### `mq`

- 负责内容：
  - 知识文件解析任务的异步投递
  - 解析任务投递失败后的 Java 内部补偿
  - 旧解析结果回传链路的兼容保留
- 当前能力：
  - 已定义 `tolink.rag.parse_task` 解析任务消息，使用扁平 snake_case JSON
  - Java 更新 `document_parsed_file.latest_parse_task_id` 后同步投递 MQ，Python 消费后创建 `document_parse_log`、负责解析并更新日志与解析聚合表
  - MQ 投递失败时事务直接回滚，不保留待补偿任务；后续如需 Outbox 或补偿表，放到三期处理
  - 旧 `parse_result` 消费者默认不装配，避免 Java 与 Python 双写解析结果
  - 已支持通过统一 `MQSend` 抽象发送消息
- 关键入口：
  - `KnowledgeParseTaskMQ`
  - `KnowledgeParseTaskService`
  - `KnowledgeParseResultMQ`（旧链路）
  - `KnowledgeParseResultKafkaReceiver`（旧链路，默认关闭）
- 关联中间件/能力：
  - MQ：Kafka / RabbitMQ 适配
  - MySQL：解析任务记录与最新解析产物

## 8. 环境与数据入口

### 8.1 关键环境变量

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` | MySQL 主机，默认 `localhost` |
| `DB_USERNAME` | 数据库用户名，默认 `root` |
| `DB_PASSWORD` | 数据库密码 |
| `REDIS_HOST` | Redis 主机，默认 `localhost` |
| `REDIS_USERNAME` | Redis 用户名 |
| `REDIS_PASSWORD` | Redis 密码 |
| `LLM_SECRET` | API Key 加密密钥，64 位十六进制字符串 |

### 8.2 数据库入口

- 建库建表：`docs/db/schema.sql`
- 初始化数据：`docs/db/init.sql`
- Mapper 位置：`link-mapper/src/main/java/com/qingluo/link/mapper/`
- Mapper XML 位置：`link-mapper/src/main/resources/mapper/`（若逐步迁移，以当前仓库实际结构为准）

### 8.3 当前数据访问说明

- 当前项目使用 MyBatis-Plus
- 业务表删除语义需以实际表结构与当前实现为准
- 新功能设计前，必须先核对实际表结构、实体类与 Mapper

### 8.4 数据设计原则

- 结构化状态、归属关系、分页查询、统计分析优先进入 MySQL
- 文件对象本体、解析中间产物、Markdown 结果优先进入对象存储
- 高读频且允许回源的数据优先进入 Redis
- 新功能设计前，应先判断数据属于“结构化元数据”还是“对象内容本体”，不要混放

### 8.5 删除与一致性原则

- 当前业务删除语义以实际实现为准，不默认全量逻辑删除
- 若涉及对象存储文件删除，必须同时考虑 DB 记录与对象存储的一致性
- 若涉及缓存更新，优先复用现有双删策略，不自行发明失效模式

### 8.6 安全原则速览

- 用户密码只以哈希形式存储，不允许明文落库
- API Key 等敏感字段必须经过加密后再存储
- 角色与登录态由 sa-token 体系统一承担
- 需要鉴权的接口必须通过统一登录与角色校验能力进入

## 9. 开发约定速览

- 包名前缀统一为 `com.qingluo.link`
- 业务异常统一继承 `BusinessException`
- 响应结构统一使用 `Result<T>` / `PageResult<T>`
- 用户身份统一从 `AuthContext` 获取
- 存储前必须通过 `ApiKeyEncryptService` 加密 API Key
- DTO 应补充 Swagger `@Schema` 注解
- Controller 应补充 JavaDoc，至少说明功能、作者、版本
- 涉及接口设计时，在 `technical-design` 中统一定义接口、异常类与错误码

## 10. 关键代码入口

### 10.1 API 入口

- `link-api/src/main/java/com/qingluo/link/api/LinkApplication.java`
- `link-api/src/main/java/com/qingluo/link/api/controller/`

### 10.2 业务服务入口

- `link-service/src/main/java/com/qingluo/link/service/`
- `link-service/src/main/java/com/qingluo/link/service/impl/`

### 10.3 组件入口

- Redis：`link-components/toLink-components-redis/src/main/java/com/qingluo/link/components/redis/`
- OSS：`link-components/toLink-components-oss/src/main/java/com/qingluo/link/components/oss/`
- MQ：`link-components/toLink-components-mq/src/main/java/com/qingluo/link/components/mq/`

## 11. 全局文档入口

| 文档 | 用途 |
| --- | --- |
| `AGENTS.md` | AI 协作开发宪法 |
| `project_info.md` | 项目现状总介绍 |
| `.agents/skills/middleware-contracts/SKILL.md` | 中间件契约 skill 入口 |
| `docs/architecture/middleware_contract.md` | 跨模块公共契约规则文档 |
| `docs/architecture/middleware-components/*.md` | 中间件组件说明书 |
| `docs/module-development-files/` | 按每次模块开发归档的需求、设计、实现、测试交付文档 |
| `docs/db/schema.sql` | 数据库结构定义 |
| `docs/db/init.sql` | 初始化数据脚本 |

## 12. 推荐阅读顺序

### 12.1 任何新任务

1. `AGENTS.md`
2. `project_info.md`

### 12.2 涉及中间件或跨模块约定

3. `.agents/skills/middleware-contracts/SKILL.md`
4. `docs/architecture/middleware_contract.md`
4. 对应 `docs/architecture/middleware-components/*.md`

### 12.3 已进入某次模块期次目录

5. 当前模块当前期次目录 `feature_info.md`
6. 当前阶段对应文档

## 13. 当前分支与协作说明

- 当前框架建设分支：`chore/skill-framework`
- 旧项目介绍来源：`CLAUDE.md`
- 后续以 `project_info.md` 作为 AI 项目总介绍主入口

## 14. 最近功能变更摘要

本节用于在每次功能开发完成后更新。当前初始化版本先记录已有能力概览，后续按功能持续补充。

- 已完成用户、LLM 配置、对话、用量统计等基础管理能力
- 已完成数据集、知识原始文件、解析文件相关能力
- 2026-04-26：文件上传与解析协同重构一期已完成测试交付文档。当前一期只交付原文件上传链路：原文件上传到 `rag-raw`、原文件表记录上传事实、同名同后缀唯一约束、失败重试复用 `object_key`、上传中 1 分钟超时补偿、列表/详情/删除接口；MQ、Python 解析、解析进度和解析产物进入二期。
- 2026-04-26：文件上传与解析协同重构二期已完成 Java 端代码实现与目标自动化测试。当前二期交付解析任务创建、上传后自动解析、手动解析、防重复点击、解析任务 MQ 投递、MQ 投递失败内部补偿、Python 进度回调转 SSE、按文件列表查询解析结果；Python 端真实解析、写库和真实 MQ 消费仍需联调确认。
- 2026-04-29：文件上传表结构与业务流程重构一期已完成代码实现与测试交付，阶段目录为 `docs/module-development-files/文件上传与解析重构/一期/`。当前上传成功后由 Java 初始化一对一 `document_parsed_file`，上传阶段不创建 `document_parse_log`、不投递解析 MQ；上传配置切换为 Redis key `knowledge:file-upload:config` + YAML fallback；原文件失败原因改为稳定编码，前端响应不暴露 OSS 内部定位字段。
- 已落地 Redis、OSS、MQ 三类中间件组件

## 15. 维护要求

- 每次功能开发完成后，必须同步更新本文件
- 只记录已经落地或已确认的项目现状
- 不在本文件写流程门禁或阶段规则

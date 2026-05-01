# 中间件与跨模块统一约定

## 1. 文档定位

本文件用于记录跨模块统一契约，约束数据库、缓存、消息、对象存储、接口响应与可观测性规范。

本文件只负责记录项目长期有效的公共约定，不负责说明 skill 的使用方式。关于何时读取、何时回写、如何判断是否形成公共契约，统一由：

- `.agents/skills/middleware-contracts/SKILL.md`

负责约束。

## 2. 使用说明

- 技术设计阶段如涉及数据库、缓存、消息、对象存储、统一 API 或日志追踪规则，必须引用本文件
- 若本次开发新增或修改公共契约，应在技术方案确认或实现落地后同步更新本文件
- 若只是复用现有约定，可在阶段文档中引用本文件，不强制新增内容

## 3. 约定总览

| 约定项 | 负责的业务/功能 | 当前典型场景 |
| --- | --- | --- |
| MySQL 约定 | 结构化业务数据落库 | 用户、厂商配置、数据集、知识文件、对话、用量日志 |
| Redis 约定 | 高读频数据缓存与缓存一致性 | 用户信息缓存、LLM 配置缓存、默认配置缓存、系统厂商缓存 |
| MQ 约定 | 跨模块异步事件协作 | 知识文件解析任务投递、解析结果回传 |
| OSS 约定 | 文件对象存储与访问边界 | 原始知识文件上传、私有文件下载、解析结果文件落库 |
| API 与错误响应约定 | 对外接口交互统一性 | Controller 响应结构、异常出参、登录态接口 |
| 可观测性约定 | 排障与链路追踪 | 文件删除失败、缓存驱逐、MQ 收发、异步处理链路 |

## MySQL 约定

当前仓库的数据库约定应优先以实体类、Mapper 和服务层实现为准。当前 `docs/db/schema.sql` 是建库建表脚本，`docs/db/init.sql` 是初始化数据脚本。设计新表或改表前必须先核对现有实体与持久化代码。

### 4.1 负责的业务边界

MySQL 约定主要负责以下业务数据的持久化边界：

- 用户与权限数据
- LLM 系统厂商与用户配置数据
- 数据集与知识文件元数据
- 对话与消息数据
- 调用用量日志
- 知识文件上传配置

判断标准：

- 只要数据需要结构化查询、分页、过滤、状态流转、统计或关联关系，优先进入 MySQL 约定范围
- 若只是临时缓存、对象内容本身或异步通知，不应直接落入 MySQL 约定章节

### 4.2 表与字段命名

- 表名使用下划线命名法
- 字段名使用下划线命名法
- 主键字段默认使用 `id`
- 关联字段使用 `<entity>_id` 命名

当前代码中的典型表名：

- `sys_user`
- `llm_system_provider`
- `llm_user_config`
- `dataset`
- `knowledge_file_config`
- `document_original_file`
- `document_parsed_file`
- `chat_conversation`
- `chat_message`
- `llm_usage_log`

### 4.3 公共字段

当前实体类中普遍存在以下公共字段：

- `id`
- `created_at`
- `updated_at`

当前项目尚未形成全表统一的 `created_by` / `updated_by` / `is_deleted` 约定，新设计不要默认这些字段已经存在。

补充观察：

- 主键在当前实体中普遍使用 `@TableId(type = IdType.AUTO)`
- 时间字段普遍映射为 `createdAt` / `updatedAt`
- 状态字段因业务不同而不同，如 `status`、`is_active`、`is_default`、`upload_status`、`parse_notice_status`

### 4.4 数据约束

- 需求文档只说明数据要求，表结构细节写入 `technical_design.md`
- 技术方案必须明确新增字段是否允许为空、默认值、索引与唯一约束
- 任何跨功能公共字段规范变更，都要回写本文件
- 若涉及历史表改造，必须同时核对：
  - 实体类字段
  - Mapper 查询条件
  - Service 层状态流转
  - 初始化脚本与已有数据兼容性

### 4.7 LLM 配置能力字段约定

- `llm_system_provider.supported_models` 表示系统厂商侧模型能力目录，推荐 JSON 结构为 `{"modelName":["CHAT","EMBEDDING"]}`。
- `llm_user_config.capability` 表示用户某条 LLM 配置记录绑定的单一能力，一条记录只允许承载一个能力。
- `llm_user_config` 的用户模型唯一性维度为 `user_id + provider_id + model_name + capability`，避免同一用户为同一厂商同一模型同一能力重复配置。
- 用户默认配置的业务作用域为 `user_id + capability`，由 `is_default` 字段表达，写路径必须在同一事务内清理同用户同能力的旧默认。
- 首批能力命名使用大写字符串，当前包括 `CHAT`、`EMBEDDING`、`OCR`、`VISION`、`REASONING`、`CODE`；新增能力时必须同步更新能力校验逻辑、接口文档和初始化数据。

## Redis 约定

### 5.1 负责的业务边界

Redis 约定主要负责“高频读取、可回源、需要缓存一致性”的业务数据，不负责替代数据库做最终真相存储。

当前主要服务于：

- 登录后用户信息读取
- 用户角色查询辅助
- LLM 用户配置读取
- 用户默认配置选择
- 系统厂商信息读取

判断标准：

- 如果数据可以从 DB 回源，且读频明显高于写频，优先考虑进入 Redis 约定
- 如果数据是一次性异步事件、文件实体内容或强事务主数据，不应优先放进 Redis

### 5.2 Key 命名

- 统一采用冒号分层格式：`<domain>:<type>:<identifier>`

当前代码中已经落地的 key：

- `user:info:{userId}`
- `user:role:{userId}`
- `llm:cfg:{configId}`
- `llm:u_def:{userId}`
- `llm:pvd:{providerType}`

### 5.3 Key 与业务责任映射

| Key 模式 | 负责的业务功能 | 当前使用方 |
| --- | --- | --- |
| `user:info:{userId}` | 用户基础信息与角色缓存 | `UserCacheService`、`AuthServiceImpl`、`StpInterfaceImpl` |
| `user:role:{userId}` | 用户角色快速失效 | `CacheConsistencyService` 联合删除目标 |
| `llm:cfg:{configId}` | 用户 LLM 配置缓存 | `UserLLMConfigServiceImpl` 写路径驱逐目标 |
| `llm:u_def:{userId}` | 用户按能力默认配置映射缓存 | `UserLLMConfigCacheService`、`UserLLMConfigServiceImpl` 联动驱逐目标 |
| `llm:pvd:{providerType}` | 系统厂商信息缓存 | `AdminProviderServiceImpl` 写路径驱逐目标 |
| `knowledge:file-upload:config` | 知识文件上传大小、后缀白名单等运行时覆盖配置 | `KnowledgeFileConfigCacheService`、`KnowledgeFileRuntimeConfigService`、管理端上传配置接口 |

### 5.4 TTL 策略

- 当前已确认的 TTL：
  - `user:info:{userId}`：7 天
- 空值缓存默认 TTL：
  - 统一空值占位：60 秒
- TTL 抖动默认值：
  - 统一抖动上限：300 秒
- 当前明确不设置 TTL 的 key：
  - `knowledge:file-upload:config`：运行时配置覆盖值，Redis 无值、不可用或配置非法时回退 YAML 默认配置
- 其余 key 若在代码中补充缓存写入逻辑，必须在技术方案中补充 TTL 说明
- 不允许在没有说明 TTL 和失效方式的前提下引入新缓存

LLM 配置缓存 TTL：

- `llm:cfg:{configId}`：1 天，缓存单条用户 LLM 配置记录，可从 `llm_user_config` 回源。
- `llm:u_def:{userId}`：1 天，缓存用户默认配置映射，业务语义为 `capability -> configId`；当前 Java owner service 的序列化对象为 `{"configIds":{"CHAT":10001}}`。
- 以上 key 统一复用 `CacheReadProtectionService`，具备空值缓存、单 key 回源合并和 TTL 抖动能力。

### 5.5 一致性策略

- 当前 Redis 组件已提供 `CacheConsistencyService`
- 当前统一一致性策略为：
  - 写库成功后同步删除缓存
  - 主请求默认在 600ms 总预算内做快速重试
  - 预算耗尽后默认主请求失败，由调用方重试；测试或特殊环境可通过 `tolink.cache-consistency.sync-delete-required=false` 放行
  - MySQL binlog 变更通过 `tolink.cache.evict` 事件触发二次删除补偿
- 当前统一读保护策略为：
  - 空值缓存
  - 单 key 进程内回源合并
  - TTL 抖动
- `llm:cfg:{configId}` 与 `llm:u_def:{userId}` 写路径采用写库成功后同步删缓存；Canal / CDC 补偿事件到达后可再次删除对应 key。
- 更新、删除、设置默认或创建默认配置时，必须同时考虑配置详情缓存与用户默认映射缓存失效，避免默认映射指向已变更或已删除配置。
- `knowledge:file-upload:config` 采用整份配置覆盖写入，不走双删；上传请求在开始时读取一份配置快照，管理员修改配置不影响已进入校验或上传中的请求。
- 若引入新的缓存更新模式，必须在技术方案中说明一致性与回滚风险

## MQ 约定

### 6.1 负责的业务边界

MQ 约定主要负责跨模块异步协作，不负责替代 API 调用做同步主链路返回。

当前主要服务于：

- 知识文件上传后的解析任务投递
- 解析结果从外部解析侧回传 Java 服务
- 历史解析结果消息兼容消费
- 缓存补偿二次删除

判断标准：

- 如果链路需要解耦、异步处理、跨服务协同、失败重试或削峰，优先考虑进入 MQ 约定
- 如果链路必须同步返回结果且依赖强事务上下文，不应优先进入 MQ

### 6.2 命名规则

- Topic、Exchange、Queue、Consumer Group 命名必须体现业务域和用途
- 当前代码中已落地的 MQ 名称：
  - `tolink.rag.parse_task`
  - `tolink.rag.parse_result`
  - `tolink.cache.evict`
- 当前 Kafka 消费组默认值：
  - `tolink-java-parse-result-worker`
  - `tolink-cache-evict`
- 新增命名建议继续沿用 `tolink.<domain>.<action>` 风格

### 6.3 MQ 名称与业务责任映射

| MQ 名称 / Group | 负责的业务功能 | 说明 |
| --- | --- | --- |
| `tolink.rag.parse_task` | 投递原始知识文件解析任务 | 由知识文件上传链路发送给解析侧 |
| `tolink.rag.parse_result` | 回传解析终态结果 | 由解析侧发送，Java 服务侧校验后转发 SSE，不写解析聚合表 |
| `tolink-java-parse-result-worker` | 解析结果消费组 | Kafka 默认消费者组 |
| `tolink.cache.evict` | 缓存补偿二次删除 | Canal / CDC 桥把 binlog 变更转成扁平缓存补偿事件 |
| `tolink-cache-evict` | 缓存补偿消费组 | Java 消费端执行二次删除补偿 |

### 6.4 消息结构

- 消息体必须具备可追踪业务主键
- 历史 `parse_result` 消息体曾采用 envelope + payload 结构，三期后不再沿用
- 三期解析结果消息 `parse_result` 采用扁平 JSON，字段包括：
  - `task_id`
  - `original_file_id`
  - `document_parse_log_id`
  - `dataset_id`
  - `user_id`
  - `task_status`
  - `failure_reason`
  - `parse_finished_at`
- 二期目标解析任务消息 `parse_task` 采用扁平 JSON，字段包括：
  - `task_id`
  - `original_file_id`
  - `parsed_file_id`
  - `user_id`
  - `dataset_id`
  - `file_type`
  - `source_bucket`
  - `source_object_key`
  - `source_filename`
  - `md_bucket`
  - `md_object_key`
- 缓存补偿消息 `cache_evict` 采用扁平 JSON，字段包括：
  - `event_id`
  - `cache_target`
  - `route_id`
  - `source_table`
  - `operation_type`
  - `trace_id`
  - `occurred_at`
- 必须说明异常重试、重复消费与幂等处理策略
- `parse_result.task_status` 只允许 `success` / `failed`
- `parse_result.failure_reason` 在 `success` 时必须为 `null`，在 `failed` 时必填
- `parse_result.parse_finished_at` 必须为带时区的 ISO 8601 字符串

### 6.5 消费约束

- 设计中必须明确消费方责任边界
- 必须说明失败处理、重试、补偿或死信策略
- 当前代码现状：
  - MQ 发送通过 `MQSend` 抽象适配 Kafka/RabbitMQ
  - 解析结果消费通过 `KnowledgeParseResultKafkaReceiver` 进入 `KnowledgeParseResultService`，按 `document_parse_log_id + task_id` 校验后转发 SSE，不写 `document_parsed_file`
  - 新链路若接入 MQ，必须说明是否沿用现有抽象和自动装配方式
- Kafka 当前仅支持普通发送，不支持模板级延迟消息

## OSS 约定

### 7.1 负责的业务边界

OSS 约定主要负责“文件对象本体”的存储与访问边界，不负责结构化元数据关系本身。

当前主要服务于：

- 原始知识文件上传
- 私有文件下载与本地缓存
- 解析结果文件的对象存储定位
- 公有/私有访问边界区分

判断标准：

- 如果内容是文件对象本体、对象 key、bucket、文件访问路径，应进入 OSS 约定
- 如果内容是文件记录状态、用户归属、解析状态等结构化字段，应进入 MySQL 约定

### 7.2 路径规则

- 当前 OSS 配置前缀为 `tolink.oss`
- 当前本地存储根目录默认为 `${user.home}/.tolink/oss`
- 当前公共目录默认：`${file-root-path}/public`
- 当前私有目录默认：`${file-root-path}/private`
- 当前知识原始文件对象 key 生成规则已落地为：

```text
{userId}/{datasetId}/{yyyy}/{MM}/{dd}/{originalFilename}
```

- 新增对象路径必须体现业务归属和层级边界，避免平铺命名
- 公共命名规则写入本文件，具体业务路径规则写入当次技术方案

### 7.3 OSS 路径与业务责任映射

| 路径 / 存储项 | 负责的业务功能 | 说明 |
| --- | --- | --- |
| `${file-root-path}/public` | 公有文件根目录 | 本地模式下的公开访问文件 |
| `${file-root-path}/private` | 私有文件根目录 | 本地模式下的私有文件与缓存目录 |
| `{userId}/{datasetId}/{yyyy}/{MM}/{dd}/{originalFilename}` | 知识原始文件对象 key | 用于原始文件归档与隔离 |
| `.notexists` 标记文件 | 私有文件下载失败缓存 | 避免重复拉取同一不存在对象 |

### 7.4 公私有策略

- 必须明确对象是公有访问还是私有访问
- 私有对象必须说明预览、下载、签名或代理访问策略
- 当前组件接口约定：
  - 公有文件上传返回可直接预览的 URL
  - 私有文件上传返回相对 `objectKey`
- 当前私有文件访问通过 `PrivateFileResolver` 处理：
  - 本地模式直接映射私有目录
  - 非本地模式按 `objectKey` 下载到本地私有缓存目录再访问
  - 下载失败会创建 `.notexists` 标记文件避免重复拉取

### 7.5 元数据约束

- 当前存储记录中常见字段包括：
  - `bucket_name`
  - `object_key`
  - `file_url`
  - `original_filename`
  - `file_suffix`
  - `content_type`
  - `file_size`
- 文件类型、归属业务、对象 key 生成规则应在技术方案中明确
- 若形成公共规则，应回写本文件

## API 与错误响应约定

### 8.1 负责的业务边界

API 与错误响应约定主要负责 Controller 对外接口的一致性，不负责内部服务之间的纯 Java 调用协议。

当前主要服务于：

- 登录、用户、厂商、配置、对话、数据集、知识文件、OSS 相关 HTTP 接口
- 错误信息对前端或调用方的统一表达

### 8.2 统一响应

- 统一使用 `Result<T>` / `PageResult<T>`
- DTO 必须补充 Swagger `@Schema` 注解

### 8.3 异常处理

- 业务异常统一继承 `BusinessException`
- 通过全局异常处理器统一出参

### 8.4 鉴权

- 需要登录的接口通过 sa-token 拦截
- 用户信息从 `AuthContext` 获取

## 可观测性约定

### 9.1 负责的业务边界

可观测性约定主要负责跨组件链路排障，不负责替代业务状态本身。

当前主要服务于：

- 文件上传、删除、下载失败排查
- 缓存驱逐与缓存未命中排查
- MQ 收发、消费失败排查
- 异步解析链路的 taskId / documentId 定位

### 9.2 约定内容

- 关键链路应具备足够日志定位信息
- 若涉及异步消息、缓存失效、文件访问等跨组件流程，技术方案应明确日志与排障点
- 如后续统一 traceId、审计日志字段或监控指标，再回写本文件
- 当前代码中，文件删除、缓存驱逐、MQ 收发失败等路径已存在日志输出；新链路应至少保留业务主键、对象 key、taskId 或用户标识中的必要信息

## 更新原则

- 新增公共契约时更新本文件
- 某次功能专属规则不写入本文件，只写入对应功能目录文档
- 若历史约定与当前实现不一致，先在 `technical_design.md` 中说明，再统一回收本文件

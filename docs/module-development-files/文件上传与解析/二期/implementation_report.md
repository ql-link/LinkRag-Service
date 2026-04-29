# 文件上传与解析协同重构二期改造报告

> **文档状态：** 已交付
> **项目名称**：toLink-Service
> **模块名称**：文件上传与解析协同重构
> **需求文档**：`docs/module-development-files/storage-file-management/二期/requirement.md`
> **技术文档**：`docs/module-development-files/storage-file-management/二期/technical_design.md`
> **分支名称**：`feature/knowledge_file_upload_and_parse`
> **负责人：** Codex
> **最后更新时间：** 2026-04-26

---

## 1. 文档修订记录

| 版本号 | 修改日期 | 修改内容简述 | 修改人 | 审核人 |
| :--- | :--- | :--- | :--- | :--- |
| v1.0 | 2026-04-26 | 记录二期解析任务、MQ、SSE 和结果查询链路实际落地情况 | Codex | 待人工审核 |

---

## 2. 改造背景与目标

### 2.1 改造背景

一期已经完成原文件上传、MinIO 存储、上传幂等、失败重试和上传超时处理。二期需要在上传成功之后补齐解析协同链路，让 Java 端负责解析任务创建与 MQ 投递，让 Python 端负责实际解析、任务状态和最新解析产物写入。

### 2.2 本次改造目标

- 支持上传成功后自动创建解析任务并投递 MQ。
- 支持用户对上传成功文件手动发起解析，且同一文件不允许重复点击创建并发中的解析任务。
- 支持前端按文件订阅 SSE 解析进度事件，Java 端只转发 Python 回调进度，不落库进度百分比。
- 支持按本次文件列表查询解析结果，返回全部文件状态，便于前端判断批量流程是否结束。
- 明确 `document_parse_task` 由 Java 创建、Python 更新，`document_parsed_file` 只保存最新成功解析产物。

### 2.3 本报告适用范围

**本报告重点说明：**

- 实际改动清单
- 实际落地位置
- 与技术方案差异
- 风险与后续事项

**本报告不重复展开：**

- 完整需求背景
- 完整技术方案推演
- 完整测试执行细节

---

## 3. 实际改造清单

### 3.1 模块改动清单

| 模块 | 改动类型 | 实际改动内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `link-api` | 修改 | 新增手动解析、SSE 订阅、解析结果查询和 Python 回调进度入口 | Controller 层 |
| `link-service` | 新增/修改 | 新增解析任务服务、SSE 服务、解析任务 MQ；上传成功后接入自动解析 | 核心业务层 |
| `link-model` | 新增/修改 | 新增解析任务实体、解析请求/响应 DTO；调整解析文件实体为最新产物职责 | 数据模型层 |
| `link-mapper` | 新增 | 新增解析任务 Mapper | MyBatis-Plus |
| `docs/db` | 修改 | 新增 `document_parse_task`，重定义 `document_parsed_file` | 初始化 SQL |
| `docs/architecture` | 修改 | 更新 MySQL、OSS、MQ 跨模块契约 | 公共契约 |

### 3.2 文件与类改动清单

| 文件/类 | 改动类型 | 实际职责变化 | 备注 |
| :--- | :--- | :--- | :--- |
| `KnowledgeParseTask` | 新增 | 映射 `document_parse_task`，记录解析任务历史和状态 | Java 创建，Python 更新 |
| `KnowledgeParsedFile` | 修改 | 改为每个原文件的最新成功解析产物表模型 | 保留旧字段为非表字段兼容旧测试 |
| `KnowledgeParseTaskMapper` | 新增 | 提供解析任务表访问入口 | 无 XML |
| `KnowledgeParseTaskServiceImpl` | 新增 | 创建解析任务、投递 MQ、处理投递失败重试、查询解析结果 | 关键链路 |
| `KnowledgeParseSseServiceImpl` | 新增 | 管理单机 SSE 连接并转发 Python 进度事件 | 多实例问题放三期 |
| `KnowledgeParseTaskMQ` | 新增 | 定义 Java 发给 Python 的解析任务 MQ | 扁平 snake_case JSON |
| `KnowledgeFileServiceImpl` | 修改 | 上传成功后按开关触发自动解析任务创建 | MQ 在事务提交后投递 |
| `KnowledgeFileController` | 修改 | 新增解析提交、解析事件订阅、解析结果查询接口 | 面向前端 |
| `InternalKnowledgeFileController` | 修改 | 新增 Python 解析进度/结果回调转发接口 | 不写 DB |
| `KnowledgeParseResultConsumer` | 修改 | 旧解析结果消费链路默认关闭 | 避免 Java 写解析结果 |

### 3.3 接口与配置改动清单

| 类型 | 名称 | 改动说明 | 影响范围 |
| :--- | :--- | :--- | :--- |
| API | `POST /api/v1/files/{fileId}/parse` | 手动提交解析任务 | 前端 |
| API | `GET /api/v1/datasets/{datasetId}/files/parse-events` | SSE 订阅单个文件解析事件 | 前端 |
| API | `GET /api/v1/datasets/{datasetId}/files/parse-results` | 按文件列表查询解析结果 | 前端 |
| API | `POST /api/v1/internal/parse-tasks/{taskId}/events` | Python 回调 Java 推送进度/结果事件 | Python 服务 |
| 配置项 | `tolink.knowledge-file.parse-dispatch-retry-interval-seconds` | 解析 MQ 投递失败补偿间隔 | Java 服务 |
| 配置项 | `tolink.knowledge-file.parse-dispatch-max-retry-count` | 解析 MQ 投递最大内部重试次数 | Java 服务 |
| 配置项 | `tolink.knowledge-file.sse-timeout-ms` | SSE 连接超时时间 | Java 服务 |
| 配置项 | `tolink.knowledge-file.legacy-parse-result-enabled` | 是否启用旧解析结果 MQ 消费链路 | 默认关闭 |
| 定时任务 | `compensateCreatedTasksOnSchedule` | 定时补偿 `created` 状态解析任务 MQ 投递 | Java 服务 |

### 3.4 数据与中间件改动清单

| 组件 | 名称/Key/Topic/Path | 改动说明 | 是否涉及契约更新 |
| :--- | :--- | :--- | :--- |
| MySQL | `document_parse_task` | 新增解析任务记录表 | 是 |
| MySQL | `document_parsed_file` | 改为最新成功解析产物表，新增成功解析次数 | 是 |
| MQ | `tolink.rag.parse_task` | Java 投递解析任务给 Python | 是 |
| OSS | `rag-raw` | 原文件读取 bucket | 是 |
| OSS | `rag-md` | Markdown 解析产物写入 bucket | 是 |
| Redis | 无 | 二期不引入 Redis | 否 |

---

## 4. 实际落地实现说明

### 4.1 核心实现路径

上传链路在原文件写库和 MinIO 上传成功后，如果前端开启“上传后立即解析”，Java 创建 `document_parse_task` 记录并在事务提交后投递 `tolink.rag.parse_task` MQ。手动解析时，Controller 校验用户与文件归属后调用同一解析任务服务创建任务。

Python 收到 MQ 后解析原文件，自行更新 `document_parse_task` 状态和 `document_parsed_file` 最新产物。解析过程中的百分比进度通过 Python 回调 Java 内部接口，Java 只负责将事件推送给 SSE 订阅的前端连接。

### 4.2 关键实现点

| 实现点 | 实际落地位置 | 处理说明 |
| :--- | :--- | :--- |
| 事务后投递 MQ | `KnowledgeParseTaskServiceImpl` | 通过事务同步确保任务记录提交后再发 MQ，避免 Python 收到不存在的任务 |
| 防重复点击解析 | `KnowledgeParseTaskServiceImpl` | 同一文件存在 `created` 或 `processing` 任务时拒绝再次创建 |
| MQ 失败内部重试 | `KnowledgeParseTaskServiceImpl` | 投递失败保留 `created` 状态，定时任务按最大次数补偿；已成功投递过的 `created` 任务不重复投递 |
| MQ 消息契约 | `KnowledgeParseTaskMQ` | 使用 `@JSONField` 固定 snake_case 字段名 |
| SSE 进度转发 | `KnowledgeParseSseServiceImpl` | 以 `fileId` 维护单机连接集合，只转发事件不落库 |
| 结果查询 | `KnowledgeParseTaskServiceImpl` | 按文件列表聚合上传状态、最新任务状态和最新解析产物 |

### 4.3 关键注释与设计意图

- 在解析任务 MQ 中说明扁平 JSON 和 `task_id` 业务幂等含义，避免后续误改为 envelope。
- 在任务投递逻辑中说明“事务提交后投递”的原因，保障 Python 不会消费到未提交任务。
- 在 SSE 服务中说明二期仅支持单机内存连接，多实例一致性问题留到三期。
- 在旧解析结果消费者中通过配置开关说明默认关闭原因，避免 Java 与 Python 同时写解析结果。

### 4.4 未纳入实现的部分

- 不实现解析任务超时处理，由 Python 端按约定更新失败状态。
- 不实现 SSE 多实例连接路由或广播，放三期。
- 不实现原文件软删除和解析文件物理删除，放三期。
- 不实现 Kafka 事务消息或 outbox，MQ 投递失败升级治理放三期。

---

## 5. 与技术方案差异说明

### 5.1 差异清单

| 技术方案项 | 原方案 | 实际实现 | 差异原因 | 是否已重新确认 |
| :--- | :--- | :--- | :--- | :--- |
| MQ 消息字段命名 | 扁平 JSON | 扁平 JSON 且通过 `@JSONField` 固定 snake_case | 测试发现 Fastjson 默认输出 camelCase，需显式约束 | 是 |
| 旧解析结果链路 | 二期不使用 Java 写解析结果 | 保留旧代码但默认不装配消费者 | 降低回滚风险，同时避免双写 | 是 |
| SSE 多实例问题 | 不在二期解决 | 明确单机内存实现并在三期治理 | 避免引入 Redis 或额外复杂度 | 是 |

### 5.2 差异影响分析

- 对上游调用的影响：前端新增解析提交、SSE 订阅和结果查询调用；上传接口继续兼容一期行为。
- 对下游模块的影响：Python 端需要按 MQ 契约消费任务，并按表约定写任务状态和最新解析产物。
- 对测试范围的影响：新增 MQ 消息契约测试、解析任务创建测试、防重复提交测试、结果查询测试和旧 MQ 默认关闭测试。
- 对发布与回滚的影响：旧解析结果消费者默认关闭，如需回滚旧链路可通过配置开关重新启用，但二期新表结构仍需同步处理。

---

## 6. 风险、遗留问题与后续事项

### 6.1 当前已知风险

- SSE 当前为单机内存连接，多实例部署时 Python 回调节点与前端订阅节点不一致会导致事件不可见。
- MQ 投递失败仅做 Java 内部定时补偿，已成功投递过的 `created` 任务不会重复投递；未引入事务消息或 outbox，极端宕机场景仍可能需要人工补偿。
- Python 直接写库需要严格遵守 Java 端表结构和状态约定。

### 6.2 遗留问题

- 原文件软删除与解析产物删除链路放三期。
- 解析历史版本选择、下载、预览不在二期范围。
- 解析任务超时状态由 Python 负责，Java 二期不兜底处理。

### 6.3 后续建议动作

- 三期评估 SSE 多实例方案，优先考虑后端统一查询或事件广播机制。
- 三期评估 MQ 投递可靠性升级方案，例如 outbox 或 Kafka 事务能力。
- 与 Python 端联调 `tolink.rag.parse_task` 消息体和回调接口。

---

## 7. 回写检查

| 检查项 | 是否完成 | 备注 |
| :--- | :--- | :--- |
| `feature_info.md` 已回填实现摘要 | 是 | 已回填实际完成摘要 |
| `middleware_contract.md` 已按需更新 | 是 | 已更新 MySQL、MQ、OSS 约定 |
| `project_info.md` 已按需更新 | 否 | 测试与交付阶段统一更新 |
| 已通知测试阶段关注实现差异 | 是 | 关注 SSE 单机、旧 MQ 默认关闭和 Python 写库约定 |
